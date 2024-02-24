package eu.solven.hutter_prize.reversible.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.reversible.AStringColumnEditorPreprocessor;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
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
				LOGGER.debug("`{}` -> `{}` (`{}` -> `{}`)", string, newString, from, to);
			} else {
				LOGGER.info(".length(): `{}` -> `{}` (`{}` -> `{}`)", string.length(), newString.length(), from, to);
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
		int depthForReplacing = 3;
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

		LOGGER.info("We would apply: {}", replacements);

		return Map.of("replacements", replacements);
	}

	private Optional<Entry<String, String>> findNextReplacement(List<?> list, int depthForReplacing, int depth) {
		AtomicLongMap<IntList> intsToCount = AtomicLongMap.create();

		int listSize = list.size();
		for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {

			// Each char is attached to its int value
			Object rawInput = list.get(stringIndex);
			if (rawInput instanceof String) {
				String rawAsString = rawInput.toString();

				scanSubStrings(depth, intsToCount, rawAsString);
			}
		}

		Int2LongMap lastOfLeftToCount = new Int2LongOpenHashMap();
		// Object2LongMap<IntList> last2OfLeftToCount = new Object2LongOpenHashMap<>();
		Int2LongMap firstOfRightToCount = new Int2LongOpenHashMap();
		// Object2LongMap<IntList> first2OfRightToCount = new Object2LongOpenHashMap<>();

		intsToCount.asMap().forEach((l, count) -> {
			if (l.size() != 3) {
				return;
			}

			// We use `Math.max` as we want to score based on the single best 3-blocks benefiting from this replacement
			// If we sum, we would have a high-score even if it was spread along a large number of 3-blocks, while these
			// 3-blocks has low probability of being replaced (as they are scarse)
			int lastOfLeft = l.getInt(l.size() - 1);
			lastOfLeftToCount.mergeLong(lastOfLeft, count, (a, b) -> Math.max(a, b));
			// last2OfLeftToCount.mergeLong(l.subList(1, 3), count, (a, b) -> Math.max(a, b));

			int firstOfRight = l.getInt(0);
			firstOfRightToCount.mergeLong(firstOfRight, count, (a, b) -> Math.max(a, b));
			// first2OfRightToCount.mergeLong(l.subList(0, 2), count, (a, b) -> Math.max(a, b));
		});

		List<IntList> length3 =
				intsToCount.asMap().keySet().stream().filter(l -> l.size() == 3).collect(Collectors.toList());

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

		int replacementsLength = 2;
		assert noneExistingBlockWithFrequentChars.stream().allMatch(l -> l.size() == replacementsLength);

		List<Map.Entry<IntList, IntList>> candidates = intsToCount.asMap()
				.entrySet()
				.stream()
				// We need at least to replace 2 characters (into 1)
				.filter(e -> e.getValue() > 1)
				// As we generated replacement with size==2, we look for blocks of size at least 3
				.filter(e -> e.getKey().size() > replacementsLength && e.getKey().size() <= depthForReplacing)
				.flatMap(e -> noneExistingBlockWithFrequentChars
						.stream().<Map.Entry<IntList, IntList>>map(replacee -> Map.entry(e.getKey(), replacee)))
				.filter(e -> !isForbidden(intsToCount, e.getKey(), e.getValue()))
				.collect(Collectors.toList());

		int size = candidates.size();
		Object2LongMap<Map.Entry<IntList, IntList>> pairToScore = new Object2LongOpenHashMap<>(size);
		candidates.forEach(e -> pairToScore.put(e, score(intsToCount, e, lastOfLeftToCount, firstOfRightToCount)));

		Optional<Map.Entry<String, String>> nextEntry = candidates.stream()
				.sorted(Comparator.comparing(e -> pairToScore.getLong(e)).reversed())
				.limit(noneExistingBlockWithFrequentChars.size())
				// We consider only strictly beneficial replacements
				.filter(e -> pairToScore.getLong(e) > 0)
				.flatMap(e -> {
					IntList toArray = e.getValue();

					long earnings = pairToScore.getLong(e);

					String from = toString(e.getKey());
					String to = toString(toArray);

					LOGGER.info("`{}` -> `{}` would spare {}",
							HPUtils.encodeWhitespaceCharacters(from),
							HPUtils.encodeWhitespaceCharacters(to),
							earnings);

					// selectedReplacements.put(from, to);

					// We need Serializability
					Map.Entry<String, String> entry = new AbstractMap.SimpleImmutableEntry<>(from, to);
					return Stream.of(entry);
				})
				// We select the first as this replacement would change the initial problem (both in frequencies, and
				// absent blocks)
				.findFirst();

		return nextEntry;
	}

	private long score(AtomicLongMap<IntList> intsToCount,
			Map.Entry<IntList, IntList> e,
			Int2LongMap lastOfLeftToCount,
			Int2LongMap firstOfRightToCount) {
		IntList from = e.getKey();
		IntList to = e.getValue();

		long count = intsToCount.get(from);
		// TODO With UTF8, the cost is variable from 1byte to 4 bytes per codePoint
		long costBefore = from.size() * count;

		long costAfterData = to.size() * count;

		// We pay the from to mapping
		// We pay an additional 2 for margin
		long newCostDictionary = from.size() + to.size();
		long marginForVariousCosts = 2;

		long scoreWithoutBonus = costBefore - costAfterData - newCostDictionary - marginForVariousCosts;

		if (scoreWithoutBonus < 0) {
			// We apply the bonus only if we have a hard-win.
			// If the compressionRatio is immediately negative, we should not include further orders.
			// Indeed, the bonus should be considered as saving only if we know the generated pattern will be leverages.
			return scoreWithoutBonus;
		}

		long bonusPointsNextIteration =
				lastOfLeftToCount.get(to.getInt(0)) + firstOfRightToCount.get(to.getInt(to.size() - 1));

		long score = scoreWithoutBonus + bonusPointsNextIteration;
		// if (score == 910) {
		// System.out.println();
		// }
		return score;
	}

	private void scanSubStrings(int depth, AtomicLongMap<IntList> intsToCount, String rawAsString) {
		IntList codePoints = IntArrayList.wrap(rawAsString.codePoints().toArray());

		// ~We start from one as we need to know which 1-char codePoints are free for replacement~
		for (int i = 2; i <= depth; i++) {
			for (int j = 0; j < codePoints.size() - i; j++) {
				intsToCount.incrementAndGet(codePoints.subList(j, j + i));
			}
		}
	}

	private Optional<IntList> notConflictingitSelf(AtomicLongMap<IntList> intsToCount,
			List<? extends IntList> candidates,
			IntList replaced) {
		// We do a copy as we will remove when accepting
		List<IntList> candidatesCopy = new ArrayList<>(candidates);

		return candidatesCopy.stream().filter(c -> {
			return !isForbidden(intsToCount, replaced, c);
		})
				// candidates are ordered from better to worst
				.findFirst();
	}

	private boolean isForbidden(AtomicLongMap<IntList> intsToCount, IntList from, IntList to) {
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
