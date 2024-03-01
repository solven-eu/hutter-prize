package eu.solven.hutter_prize.reversible.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.reversible.AStringColumnEditorPreprocessor;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 * We analyze repetitive consecutive blocks, and how to replace them by shorter blocks.
 * 
 * @author Benoit Lacelle
 *
 */
@Deprecated(since = "By analyzing blocks, we are roughly re-implementing LZ related compression")
public class CharactersBlockAnalysisPreprocessor extends AStringColumnEditorPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CharactersBlockAnalysisPreprocessor.class);

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		List<Map.Entry<String, String>> replacements = (List<Entry<String, String>>) context.get("replacements");

		for (Entry<String, String> oneReplacement : replacements) {
			String from = oneReplacement.getKey();
			String to = oneReplacement.getValue();
			String newString = string.replaceAll(Pattern.quote(from), Matcher.quoteReplacement(to));
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("`{}` -> `{}` (`{}` -> `{}`)",
						string,
						newString,
						HPUtils.encodeWhitespaceCharacters(from),
						HPUtils.encodeWhitespaceCharacters(to));
			} else {
				LOGGER.info(".length(): `{}` -> `{}` (`{}` -> `{}`)",
						string.length(),
						newString.length(),
						HPUtils.encodeWhitespaceCharacters(from),
						HPUtils.encodeWhitespaceCharacters(to));
			}

			string = newString;
		}

		return string;
	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		List<Map.Entry<String, String>> replacements = (List<Entry<String, String>>) context.get("replacements");

		for (Entry<String, String> oneReplacement : Lists.reverse(replacements)) {
			String from = oneReplacement.getKey();
			String to = oneReplacement.getValue();
			String newString = string.replaceAll(Pattern.quote(to), Matcher.quoteReplacement(from));
			LOGGER.debug("`{}` <- `{}` (`{}` <- `{}`)", newString, string, from, to);
			string = newString;
		}

		return string;
	}

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		// We want to capture ` the `, `&amp;`, `</ref>`
		// We should compute dynamically when it is better to stop considering bigger blocks
		// There is no point in catching large blocks as this is applied recursively: `abcdefgh` would be turned into
		// `xyfgh` and itself into `yz`. If we push this to the extreme, we could consider only block of size 3 to be
		// replaced by blocks of size 2. However, handling only `3->2` leads to dictionary over-wait as we decrease in
		// size quite slowly.
		int depthForReplacing = 5;
		// Will be applied only the left. See `notConflictingitSelf`
		int marginForReplacementAnalysis = 0;
		int depth = depthForReplacing + marginForReplacementAnalysis;

		// This is not a Map as the ordering is very important
		List<Map.Entry<String, String>> replacements = new ArrayList<>();

		List<?> compressedList = list;

		while (true) {
			Optional<Map.Entry<String, String>> optNextReplacement =
					findNextReplacement(compressedList, depthForReplacing, depth);

			if (optNextReplacement.isPresent()) {
				Map.Entry<String, String> nextReplacement = optNextReplacement.get();
				replacements.add(nextReplacement);

				// Mutate the considered List, to search for the next optimal replacement
				try {
					compressedList = (List<?>) process(true,
							(IProcessString) this::compressString,
							compressedList,
							Optional.of(Map.of("replacements", Collections.singletonList(nextReplacement))));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}

			} else {
				break;
			}
		}

		LOGGER.info("We would apply: {}{}",
				System.lineSeparator(),
				replacements.stream().map(e -> e.toString()).collect(Collectors.joining(System.lineSeparator())));

		return Map.of("replacements", replacements);
	}

	protected void scanSubStrings(int minDepth, int maxDepth, Object2LongMap<IntList> intsToCount, String rawAsString) {
		IntList codePoints = IntArrayList.wrap(rawAsString.codePoints().toArray());

		// ~We start from one as we need to know which 1-char codePoints are free for replacement~
		for (int i = minDepth; i <= maxDepth; i++) {
			Set<IntList> previousCountedAsSet = new HashSet<>();
			Int2ObjectMap<IntList> indexToPrevious = new Int2ObjectOpenHashMap<>(i);

			for (int j = 0; j <= codePoints.size() - i; j++) {
				IntList block = codePoints.subList(j, j + i);

				boolean isCounted;
				if (previousCountedAsSet.contains(block)) {
					// Current block is rejected as it is repeating itself
					// e.g. `[aa]a` has been counted and we are processing `a[aa]`
					isCounted = false;
				} else {
					// This block has to be counted
					intsToCount.merge(block, 1, (a, b) -> a + b);
					isCounted = true;
				}
				// Shift the previous blocks by dropping the last block
				// However, this lastBlock may itself not having been counted
				int modulo = j % (i - 1);
				{
					IntList blockLeavingWindow = indexToPrevious.remove(modulo);
					if (blockLeavingWindow != null) {
						previousCountedAsSet.remove(blockLeavingWindow);
					}
				}

				if (isCounted) {
					indexToPrevious.put(modulo, block);
					previousCountedAsSet.add(block);
				}

			}
		}
	}

	protected Optional<Entry<String, String>> findNextReplacement(List<?> list, int depthForReplacing, int depth) {
		Object2LongMap<IntList> intsToCount = new Object2LongOpenHashMap<>();

		int listSize = list.size();
		for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {

			// Each char is attached to its int value
			Object rawInput = list.get(stringIndex);
			if (rawInput instanceof String) {
				String rawAsString = rawInput.toString();

				scanSubStrings(2, depth, intsToCount, rawAsString);
			}
		}

		return new OptimalComputer(intsToCount).findOptimal();
	}

	public static class OptimalComputer {
		final Object2LongMap<IntList> intsToCount;

		final Object2LongMap<IntList> leftBonusCache = new Object2LongOpenHashMap<>();
		final Object2LongMap<IntList> rightBonusCache = new Object2LongOpenHashMap<>();

		public OptimalComputer(Object2LongMap<IntList> intsToCount) {
			this.intsToCount = intsToCount;
		}

		protected Optional<Entry<String, String>> findOptimal() {
			Int2LongMap lastOfLeftToCount = new Int2LongOpenHashMap();
			// Object2LongMap<IntList> last2OfLeftToCount = new Object2LongOpenHashMap<>();
			Int2LongMap firstOfRightToCount = new Int2LongOpenHashMap();
			// Object2LongMap<IntList> first2OfRightToCount = new Object2LongOpenHashMap<>();

			intsToCount.forEach((l, count) -> {
				if (l.size() < 3) {
					return;
				}

				// We use `Math.max` as we want to score based on the single best 3-blocks benefiting from this
				// replacement
				// If we sum, we would have a high-score even if it was spread along a large number of 3-blocks, while
				// these
				// 3-blocks has low probability of being replaced (as they are scarse)
				int lastOfLeft = l.getInt(l.size() - 1);
				lastOfLeftToCount.mergeLong(lastOfLeft, count, (a, b) -> Math.max(a, b));
				// last2OfLeftToCount.mergeLong(l.subList(1, 3), count, (a, b) -> Math.max(a, b));

				int firstOfRight = l.getInt(0);
				firstOfRightToCount.mergeLong(firstOfRight, count, (a, b) -> Math.max(a, b));
				// first2OfRightToCount.mergeLong(l.subList(0, 2), count, (a, b) -> Math.max(a, b));
			});

			List<? extends IntList> noneExistingBlockWithFrequentChars =
					findBestReplacementsSize2(lastOfLeftToCount, firstOfRightToCount);

			int replacementsLength = 2;
			assert noneExistingBlockWithFrequentChars.stream().allMatch(l -> l.size() == replacementsLength);

			// System.out.println(intsToCount.getLong(IntArrayList.of('c', 'd', 'e'))); intsToCount.size()

			List<Map.Entry<IntList, IntList>> fromToCandidates = intsToCount.object2LongEntrySet()
					.stream()
					// We need at least to replace 2 characters (into 1)
					.filter(e -> e.getLongValue() > 1)
					// As we generated replacement with size==2, we look for blocks of size at least 3
					.filter(e -> e.getKey().size() > replacementsLength
					// && e.getKey().size() <= depthForReplacing
					)
					.limit(128)
					.flatMap(e -> noneExistingBlockWithFrequentChars
							.stream().<Map.Entry<IntList, IntList>>map(replacee -> Map.entry(e.getKey(), replacee)))
					.filter(e -> !isForbidden(e.getKey(), e.getValue()))
					// .limit(1024)
					.collect(Collectors.toList());

			int size = fromToCandidates.size();
			Object2LongMap<Map.Entry<IntList, IntList>> pairToScore = new Object2LongOpenHashMap<>(size);
			fromToCandidates.forEach(e -> pairToScore.put(e, score(e)));

			Object2LongMap<Map.Entry<IntList, IntList>> pairToBonus = new Object2LongOpenHashMap<>(size);

			// List<Map.Entry<String, String>> top10Score = fromToCandidates.stream()
			// .sorted(Comparator.<Map.Entry<IntList, IntList>, Long>comparing(e -> pairToScore.getLong(e))
			// // .thenComparing(Comparator.comparing(e -> pairToBonus.getLong(e)))
			// .reversed())
			// // We consider only strictly beneficial replacements
			// .filter(e -> pairToScore.getLong(e) > 0)
			// .map(e -> mapEntry(pairToScore, pairToBonus, e))
			// .limit(10)
			// .collect(Collectors.toList());

			fromToCandidates.forEach(e -> pairToBonus.put(e, bonus1stOrder(e.getKey(), e.getValue())));
			//
			// List<Map.Entry<String, String>> top10Bonus = fromToCandidates.stream()
			// .sorted(Comparator.comparing(e -> pairToBonus.getLong(e))
			// // .thenComparing(Comparator.comparing(e -> pairToBonus.getLong(e)))
			// .reversed())
			// // We consider only strictly beneficial replacements
			// .filter(e -> pairToBonus.getLong(e) > 0)
			// .map(e -> mapEntry(pairToScore, pairToBonus, e))
			// .limit(10)
			// .collect(Collectors.toList());

			// List<Map.Entry<String, String>> top10ScoreBonus = fromToCandidates.stream()
			// .sorted(Comparator.<Map.Entry<IntList, IntList>, Long>comparing(
			// e -> scoreAndBonus(pairToScore, pairToBonus, e).longValue())
			// // .thenComparing(Comparator.comparing(e -> pairToBonus.getLong(e)))
			// .reversed())
			// .filter(e -> scoreAndBonus(pairToScore, pairToBonus, e).longValue() > 0)
			// .map(e -> mapEntry(pairToScore, pairToBonus, e))
			// // .limit(10)
			// .collect(Collectors.toList());

			List<Map.Entry<String, String>> top10ScoreThenBonus = fromToCandidates.stream()
					.sorted(Comparator.<Map.Entry<IntList, IntList>, Long>comparing(e -> pairToScore.getLong(e))
							.thenComparing(Comparator.comparing(e -> pairToBonus.getLong(e)))
							.reversed())
					.filter(e -> pairToScore.getLong(e) > 0)
					.map(e -> mapEntry(pairToScore, pairToBonus, e))
					.limit(10)
					.collect(Collectors.toList());

			// pairToScore.getLong(Map.entry(IntArrayList.wrap(top10.get(1).getKey().codePoints().toArray()),
			// IntArrayList.wrap(top10.get(1).getValue().codePoints().toArray())));
			// intsToCount.get(IntArrayList.wrap(top10.get(1).getKey().codePoints().toArray()));

			Optional<Map.Entry<String, String>> nextEntry = top10ScoreThenBonus.stream()
					// We select the first as this replacement would change the initial problem (both in frequencies,
					// and
					// absent blocks)
					.findFirst();

			return nextEntry;
		}

		private Number scoreAndBonus(Object2LongMap<Map.Entry<IntList, IntList>> pairToScore,
				Object2LongMap<Map.Entry<IntList, IntList>> pairToBonus,
				Map.Entry<IntList, IntList> e) {
			long score = pairToScore.getLong(e);
			// Keep the positive part, as we will square it (a very negative value should not lead to a good bonus)
			// long positiveBonus = Math.max(0, pairToBonus.getLong(e));
			return score + pairToBonus.getLong(e);
		}

		private SimpleImmutableEntry<String, String> mapEntry(Object2LongMap<Map.Entry<IntList, IntList>> pairToScore,
				Object2LongMap<Map.Entry<IntList, IntList>> pairToBonus,
				Entry<IntList, IntList> e) {
			IntList toArray = e.getValue();

			long score = pairToScore.getLong(e);
			long bonus = pairToBonus.getLong(e);

			String from = toString(e.getKey());
			String to = toString(toArray);

			LOGGER.info("`{}` -> `{}` would spare {}{}",
					HPUtils.encodeWhitespaceCharacters(from),
					HPUtils.encodeWhitespaceCharacters(to),
					score,
					bonus >= 0 ? "+" + bonus : bonus);

			// We need Serializability
			return new AbstractMap.SimpleImmutableEntry<>(from, to);
		}

		private List<? extends IntList> findBestReplacementsSize2(Int2LongMap lastOfLeftToCount,
				Int2LongMap firstOfRightToCount) {
			List<IntList> length3 =
					intsToCount.keySet().stream().filter(l -> l.size() == 3).collect(Collectors.toList());

			List<? extends IntList> noneExistingBlockWithFrequentChars = Sets
					.cartesianProduct(
							length3.stream().mapToInt(l -> l.getInt(2)).mapToObj(i -> i).collect(Collectors.toSet()),
							length3.stream().mapToInt(l -> l.getInt(0)).mapToObj(i -> i).collect(Collectors.toSet()))
					.stream()
					.map(l -> new IntArrayList(l.stream().mapToInt(i -> i).toArray()))
					// Keep only 2blocks which does not exist yet in the input
					.filter(replacement -> !intsToCount.containsKey(replacement))
					.sorted(Comparator
							.<IntList, Long>comparing(
									l -> lastOfLeftToCount.get(l.getInt(0)) * firstOfRightToCount.get(l.getInt(1)))
							.reversed())
					.limit(128)
					.toList();
			LOGGER.info("Absent 2-chars based on frequent 3-chars: {}",
					noneExistingBlockWithFrequentChars.stream()
							.map(this::toString)
							.map(HPUtils::encodeWhitespaceCharacters)
							.collect(Collectors.toCollection(LinkedList::new)));
			return noneExistingBlockWithFrequentChars;
		}

		private long score(Map.Entry<IntList, IntList> entry) {
			IntList from = entry.getKey();
			IntList to = entry.getValue();

			long count = intsToCount.getLong(from);
			// TODO With UTF8, the cost is variable from 1byte to 4 bytes per codePoint
			long costBefore = from.size() * count;

			long costAfterData = to.size() * count;

			// We pay the from to mapping
			// We pay an additional 2 for margin
			long newCostDictionary = from.size() + to.size();
			long marginForVariousCosts = 2;

			long scoreWithoutBonus = costBefore - costAfterData - newCostDictionary - marginForVariousCosts;

			return scoreWithoutBonus;
			//
			// if (scoreWithoutBonus <= 0) {
			// // We apply the bonus only if we have a hard-win.
			// // If the compressionRatio is immediately negative, we should not include further orders.
			// // Indeed, the bonus should be considered as saving only if we know the generated pattern will be
			// // leverages.
			// return scoreWithoutBonus;
			// }
			//
			// long bonusPointsNextIteration = bonus1stOrder(from, to);
			//
			// long score = scoreWithoutBonus + bonusPointsNextIteration;
			// // if (score == 910) {
			// // System.out.println();
			// // }
			// return score;
		}

		protected long bonus1stOrder(IntList from, IntList to) {
			long maxLeft = getBonusLeftSide(from, to);
			long maxRight = getBonusRightSide(from, to);

			long bonusPointsNextIteration = maxLeft + maxRight;
			// lastOfLeftToCount.get(to.getInt(0)) + firstOfRightToCount.get(to.getInt(to.size() - 1));
			return bonusPointsNextIteration;
		}

		protected long getBonusRightSide(IntList from, IntList to) {
			int cFrom = from.getInt(from.size() - 1);
			int cTo = to.getInt(to.size() - 1);

			long maxRight;
			if (cFrom != cTo) {
				maxRight = rightBonusCache.computeIfAbsent(IntArrayList.of(cFrom, cTo), k -> {
					return intsToCount.object2LongEntrySet()
							.stream()
							// Filter 3-blocks starting like `from` end
							.filter(e -> e.getKey().size() == 3 && e.getKey().getInt(0) == cFrom)
							.mapToLong(e -> {
								IntArrayList rightOfReplacementSuffixedWithsuffixOfReplaced = IntArrayList
										.toList(IntStream.concat(IntStream.of(cTo), e.getKey().intStream().skip(1)));
								long countTo = intsToCount.getLong(rightOfReplacementSuffixedWithsuffixOfReplaced);
								long countFrom = e.getLongValue();
								return countTo - countFrom;
							})
							.max()
							.orElse(0);
				});
			} else {
				maxRight = 0;
			}
			return maxRight;
		}

		/**
		 * Turning from `abfromcd` to `abtocd`, we would break 3-blocks like `abf` and `mcd` while generating 3-blocks
		 * like `abt` and `ocd`. This estimates the gain of the left leg: from `abf` to `abt`.
		 * 
		 * @param from
		 * @param to
		 * @return
		 */
		protected long getBonusLeftSide(IntList from, IntList to) {
			int cFrom = from.getInt(0);
			int cTo = to.getInt(0);

			long maxLeft;
			if (cFrom != cTo) {
				maxLeft = leftBonusCache.computeIfAbsent(IntArrayList.of(cFrom, cTo), k -> {
					return intsToCount.object2LongEntrySet()
							.stream()
							// Filter 3-blocks are they are less numerous and representative of frequency
							// Filter patterns which ends like `replaced` start
							.filter(e -> e.getKey().size() == 3 && e.getKey().getInt(e.getKey().size() - 1) == cFrom)
							.mapToLong(e -> {
								IntList prefix = e.getKey();
								IntArrayList prefixOfReplacedSuffixedWithLeftOfReplacement = IntArrayList
										.toList(IntStream.concat(prefix.intStream().limit(prefix.size() - 1),
												IntStream.of(cTo)));
								// We are happy if we generate a pattern already frequent
								long countTo = intsToCount.getLong(prefixOfReplacedSuffixedWithLeftOfReplacement);
								// We are happy if we remove a pattern not very present
								long countFrom = e.getLongValue();
								return countTo - countFrom;
							})
							.max()
							.orElse(0);
				});
			} else {
				maxLeft = 0;
			}
			return maxLeft;
		}

		private boolean isForbidden(IntList from, IntList to) {
			// if we replace `XYZ` with `11`, we need to make sure there was no `1XYZ` pattern else we would
			// generate `111` which would be later replaced by `XYZ1`.
			// ---
			// if we replace `XYZ` with `ABC`, we need to make sure there was no `WXY`==`ABC` pattern else we would
			// generate `WXYZ`==`ABCC`==`AABC`.
			// ---
			// BEWARE Prove the only pattern is a repeating character!
			// ---
			// If we have a repeating pattern with length>1, then we consider `toto`, and we may have an issue if we we
			// replace `totititi` by `tototo`
			// ---
			// Then idea is then the new blocks created by this replacement should not create conflicting blocks
			// (especially not the block considered as absent in this very case)

			// For now, we handle only size==2
			assert to.size() == 2;
			int rightRepetitive = to.getInt(1);
			if (to.getInt(0) != rightRepetitive) {
				// Not a repetitive pattern: no danger
				return false;
			}

			// `-1` as we are not sure intsToCount holds patterns of length `from.size()+1`
			int[] rawForbidden =
					IntStream.concat(IntStream.of(rightRepetitive), from.intStream().limit(from.size() - 1)).toArray();
			IntList forbidden = IntArrayList.wrap(rawForbidden);

			// We accept only if this parent pattern does not already exist
			return intsToCount.containsKey(forbidden);
		}

		private String toString(IntList intList) {
			return new String(intList.toIntArray(), 0, intList.size());
		}
	}

}
