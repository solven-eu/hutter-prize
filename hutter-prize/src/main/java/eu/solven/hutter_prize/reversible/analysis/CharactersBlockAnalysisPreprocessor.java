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
import com.google.common.util.concurrent.AtomicLongMap;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.reversible.AStringColumnEditorPreprocessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

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
			LOGGER.debug("`{}` -> `{}`", string, newString);
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
			LOGGER.debug("`{}` -> `{}`", string, newString);
			string = newString;
		}

		return string;
	}

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		// We want to capture ` the `, `&amp;`, `</ref>`
		// We should compute dynamically when it is better to stop considering bigger blocks
		int depthForReplacing = 15;
		// Will be applied only the left. See `notConflictingitSelf`
		int marginForReplacementAnalysis = 1;
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

				IntList codePoints = IntArrayList.wrap(rawAsString.codePoints().toArray());

				// We start from one as we need to know which 1-char codePoints are free for replacement
				for (int i = 1; i <= depth; i++) {
					for (int j = 0; j < codePoints.size() - i; j++) {
						intsToCount.incrementAndGet(codePoints.subList(j, j + i));
					}
				}
			}
		}

		// We look for frequent characters
		List<IntList> mostFrequent1Char = intsToCount.asMap()
				.entrySet()
				.stream()
				// Filter 1-char entries
				.filter(e -> e.getKey().size() == 1)
				.sorted(Comparator.comparing(e -> -e.getValue()))
				.map(e -> e.getKey())
				.limit(26 + 12)
				.collect(Collectors.toList());
		LOGGER.debug("Frequent 1-char: {}",
				mostFrequent1Char.stream()
						.map(this::toString)
						.map(HPUtils::encodeWhitespaceCharacters)
						.collect(Collectors.toList()));

		// We generate block which does not exist yet, but holds frequent chars
		// We hope to generate new frequent blocks with them
		List<IntList> noneExistingBlockWithFrequentChars = Lists.cartesianProduct(mostFrequent1Char, mostFrequent1Char)
				.stream()

				// Cartesian-Product would typically generate a not-optimal ordering
				.sorted(Comparator.comparing(e -> -intsToCount.get(e.get(0)) * intsToCount.get(e.get(1))))

				.map(cp -> {
					int[] joinedArray = cp.stream().flatMapToInt(l -> l.intStream()).toArray();
					return new IntArrayList(joinedArray);
				})
				.filter(replacement -> !intsToCount.containsKey(replacement))
				.collect(Collectors.toList());
		LOGGER.debug("Absent 2-chars based on frequent 1-char: {}",
				noneExistingBlockWithFrequentChars.stream()
						.map(this::toString)
						.map(HPUtils::encodeWhitespaceCharacters)
						.collect(Collectors.toCollection(LinkedList::new)));

		int replacementsLength = 2;
		assert noneExistingBlockWithFrequentChars.stream().allMatch(l -> l.size() == replacementsLength);

		// Map<String, String> selectedReplacements = new HashMap<>();

		Optional<Map.Entry<String, String>> nextEntry = intsToCount.asMap()
				.entrySet()
				.stream()
				// We need at least to replace 2 times
				.filter(e -> e.getValue() > 1)
				// As we generated replacement with size==2, we look for blocks of size at least 3
				.filter(e -> e.getKey().size() > replacementsLength && e.getKey().size() <= depthForReplacing)
				.sorted(Comparator.comparing(e -> score(replacementsLength, e)))
				.limit(noneExistingBlockWithFrequentChars.size())
				// We consider only strictly beneficial replacements
				.filter(e -> score(replacementsLength, e) < 0)
				.flatMap(e -> {
					Optional<IntList> optToArray =
							notConflictingitSelf(intsToCount, noneExistingBlockWithFrequentChars, e.getKey());
					if (optToArray.isEmpty()) {
						return Stream.empty();
					}

					IntList toArray = optToArray.get();

					// long existingCost = existingCost(e);
					long earnings = -score(replacementsLength, e);

					String from = toString(e.getKey());

					String to = new String(toArray.toIntArray(), 0, toArray.size());

					LOGGER.info("`{}` -> `{}` would spare {}",
							HPUtils.encodeWhitespaceCharacters(from),
							HPUtils.encodeWhitespaceCharacters(to),
							earnings);

					// selectedReplacements.put(from, to);

					Map.Entry<String, String> entry = new AbstractMap.SimpleImmutableEntry<>(from, to);
					return Stream.of(entry);
				})
				// We select the first as this replacement would change the initial problem (both in frequencies, and
				// absent blocks)
				.findFirst();

		return nextEntry;
	}

	private Optional<IntList> notConflictingitSelf(AtomicLongMap<IntList> intsToCount,
			List<IntList> candidates,
			IntList replaced) {
		// Remove last not to shift the whole array
		// List<IntList> reversed = Lists.reverse(candidates);

		// We do a copy as we will remove when accepting
		List<IntList> candidatesCopy = new ArrayList<>(candidates);

		return candidatesCopy.stream().filter(c -> {
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
			assert c.size() == 2;
			if (c.getInt(0) != c.getInt(1)) {
				return true;
			}

			IntList forbidden =
					IntArrayList.wrap(IntStream.concat(IntStream.of(c.getInt(1)), replaced.intStream()).toArray());

			// We accept only if this parent pattern does not already exist
			return !intsToCount.containsKey(forbidden);
		})
				.peek(candidates::remove)
				// candidates are ordered from better to worst
				.findFirst();
	}

	private String toString(IntList intList) {
		return new String(intList.toIntArray(), 0, intList.size());
	}

	private long score(int replacementsLength, Map.Entry<IntList, Long> e) {
		long existingCost = existingCost(e);

		long newCostInput = replacementsLength * e.getValue();
		// We pay the from to mapping
		// We pay an additional 2 for margin
		long newCostDictionary = e.getKey().size() + replacementsLength + 3;

		return (newCostInput + newCostDictionary) - existingCost;
	}

	private long existingCost(Map.Entry<IntList, Long> e) {
		// TODO With UTF8, the cost is variable from 1byte to 4 bytes per codePoint
		return e.getKey().size() * e.getValue();
	}

}
