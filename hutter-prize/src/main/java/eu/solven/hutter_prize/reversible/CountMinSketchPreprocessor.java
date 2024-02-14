package eu.solven.hutter_prize.reversible;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.reversible.stream_count.CountMinSketch;
import eu.solven.hutter_prize.reversible.stream_count.Murmur3;

/**
 * Rely on a HMM to detect pattern. This is especially useful to detect symbolic patterns (e.g. `&amp;` which is
 * redundant in XML encoding HTML).
 * 
 * Once detected, such pattern will be replaced by a smaller pattern.
 * 
 * @author Benoit Lacelle
 *
 */
// https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h
public class CountMinSketchPreprocessor extends ASymbolsPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CountMinSketchPreprocessor.class);

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		// CountMinSketch could be used not to retain all patterns
		// We did not find yet a way to detect that a pattern is frequent when it is inserted. We may need to apply it
		// over a subset of the inputs, and retain high-frequency patterns detected over a second subset/rest of the
		// text. Or init frequencies over whole text, and then search again through a subset
		CountMinSketch cms = new CountMinSketch(0.00001F, 0.00001F);
		// TopKDataStream<String> streamSummary = new TopKDataStream<String>(1024 * 4, new Random(0));

		// This will keep in priority entries with highest probability
		// MinMaxPriorityQueue<Map.Entry<String, Double>> stringToProbability =
		// MinMaxPriorityQueue.<Map.Entry<String, Double>>orderedBy(Comparator.comparing(e -> -e.getValue()))
		// .maximumSize(128)
		// .create();

		long total = 0;
		//
		// int minCount = 0;

		// We search for patterns like `&gt;` or `[a]`. We also looks for patterns of length 2 as it make it easy to
		// understand patterns like `[[`
		int minL = 2;
		int maxAdditionalLength = 8;

		Map<Integer, AtomicLongMap<String>> lengthToPatterns = new HashMap<>();

		// We also looks for patterns like `*'''w'''`
		for (int patternLength = minL; patternLength <= minL + maxAdditionalLength; patternLength++) {
			LOGGER.info("Start length={}", patternLength);

			// We record all patterns: while it may grow very large, it is kind of limited as we will get ride of words
			AtomicLongMap<String> patternToCount = AtomicLongMap.create();
			lengthToPatterns.put(patternLength, patternToCount);

			int listSize = list.size();
			for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {
				// Each char is attached to its int value
				Object rawInput = list.get(stringIndex);
				if (rawInput instanceof String) {
					String asString = rawInput.toString();

					String simplified = Phd9Preprocessor.compactWords(asString);

					int[] codePoints = simplified.codePoints().toArray();

					for (int s = 0; s < codePoints.length - patternLength; s++) {
						int maxL = Math.min(minL + 5, codePoints.length - s);
						for (int l = minL; l < maxL; l++) {
							int count = setAndGet(cms, codePoints, patternLength, s);
							total += 1;
						}
					}

				}
			}

			report(cms, patternToCount.asMap().entrySet());
		}

		proposeNewPatterns(minL, maxAdditionalLength, lengthToPatterns);

		return Map.of();
	}

	private void proposeNewPatterns(int minL,
			int maxAdditionalLength,
			Map<Integer, AtomicLongMap<String>> lengthToPatterns) {
		Set<String> candidateParenthesisPatterns = prepareParentjesisCandidates(lengthToPatterns);

		Map<String, String> acceptedPatternToReplacement = new TreeMap<>();

		Stream.of("[[A]]").forEach(oldPattern -> {
			long count = lengthToPatterns.get(oldPattern.length()).get(oldPattern);

			checkAndRegisterPattern(lengthToPatterns,
					acceptedPatternToReplacement,
					candidateParenthesisPatterns,
					Maps.immutableEntry(oldPattern, count));
		});

		for (int i = minL + maxAdditionalLength; i >= minL; i--) {
			AtomicLongMap<String> patternToCount = lengthToPatterns.get(i);
			LOGGER.info("Trying to find good patterns of length {}", i);

			// We filter relevant patterns very lately, as various processes will rely on an exhaustive list of patterns
			{
				patternToCount.size();
				patternToCount.asMap()
						.entrySet()
						.stream()
						.filter(e -> e.getValue().longValue() == 1)
						.map(e -> e.getKey())
						.forEach(patternToCount::remove);

				long sum = patternToCount.sum();
				AtomicLong cumulatedSum = new AtomicLong();
				patternToCount.asMap()
						.entrySet()
						.stream()
						// We keep most frequent patterns, in order to drop the long-tail
						.sorted(Comparator.comparing(e -> -e.getValue()))
						.forEach(e -> {
							if (cumulatedSum.addAndGet(e.getValue().longValue()) > sum * 0.8) {
								patternToCount.remove(e.getKey());
							}
						});
			}

			patternToCount.asMap()
					.entrySet()
					.stream()
					// Consider only patterns relating to a single word or group of words
					// It should catch things like `*'''z'''`
					.filter(e -> CharMatcher.anyOf("wz").countIn(e.getKey()) == 1)
					.sorted(Comparator.comparing(e -> -e.getValue()))
					.forEach(oldPatternEntry -> {
						String oldPattern = oldPatternEntry.getKey();

						if (!oldPattern.equals(oldPattern.trim())) {
							LOGGER.debug(
									"We skip patterns which can be trimmed. They may be considered in a later steps");
							return;
						}

						checkAndRegisterPattern(lengthToPatterns,
								acceptedPatternToReplacement,
								candidateParenthesisPatterns,
								oldPatternEntry);

					});
			;
		}
	}

	private void checkAndRegisterPattern(Map<Integer, AtomicLongMap<String>> lengthToPatterns,
			Map<String, String> acceptedPatternToReplacement,
			Set<String> candidateParenthesisPatterns,
			Entry<String, Long> oldPatternEntry) {

		String oldPattern = oldPatternEntry.getKey();

		List<MatchResult> matcherResults = Pattern.compile("[wzA]").matcher(oldPattern).results().toList();
		if (matcherResults.size() != 1) {
			throw new IllegalStateException("Expected single result: " + matcherResults);
		}

		MatchResult singleResult = matcherResults.get(0);

		String wordOrWords = singleResult.group();
		String prefix = oldPattern.substring(0, singleResult.start());
		String suffix = oldPattern.substring(singleResult.end());

		if (prefix.length() >= 1 && suffix.length() >= 1) {
			Optional<String> optNewPattern = candidateParenthesisPatterns.stream()
					.filter(p -> "A".equals(wordOrWords) || p.contains(wordOrWords))
					.findAny();

			if (optNewPattern.isEmpty()) {
				LOGGER.warn("Lack patterns to compact `{}`", HPUtils.encodeWhitespaceCharacters(oldPattern));
			} else {
				// This is a pattern like `#{w}`
				String newPattern = optNewPattern.get();

				if (lengthToPatterns.get(newPattern.length()).containsKey(newPattern)) {
					LOGGER.warn("TODO Add alternative newPattern strategy as `{}` is already present", newPattern);
				} else {
					long benefit = oldPatternEntry.getValue().longValue() * (oldPattern.length() - newPattern.length());
					LOGGER.info("Could replace `{}` by `{}`. It would benefit: {}", oldPattern, newPattern, benefit);

					lengthToPatterns.values()
							.stream()
							.flatMap(am -> am.asMap().entrySet().stream())
							.filter(
									// `[w]` is removed if `[[w]]` is accepted
									ee -> oldPattern.contains(ee.getKey()) ||
									// `[[w]]` is removed if `[w]` is accepted
											oldPattern.contains(ee.getKey()))
							.forEach(ee -> {
								String subPattern = ee.getKey();
								LOGGER.info("Dropping {} given {}", subPattern, oldPattern);
								lengthToPatterns.get(subPattern.length()).remove(subPattern);
							});

					acceptedPatternToReplacement.put(oldPattern, newPattern);
				}
			}
		}
	}

	private Set<String> prepareParentjesisCandidates(Map<Integer, AtomicLongMap<String>> lengthToPatterns) {
		Set<String> functionSymbols = Set.of("!", "?", "#", "_", "-", "|");
		Set<String> parenthesisSymbols = Set.of("()", "{}", "[]", "__", "--", "||");

		Set<String> candidateParenthesisPatterns =
				Sets.cartesianProduct(functionSymbols, parenthesisSymbols).stream().flatMap(l -> {
					String pattern = l.get(0) + l.get(1).charAt(0) + "w" + l.get(1).charAt(1);

					return Stream.of(pattern, pattern.replace('w', 'z'));
				}).filter(p -> !lengthToPatterns.get(p.length()).containsKey(p)).collect(Collectors.toSet());

		LOGGER.info("We have {} parenthesis candidates", candidateParenthesisPatterns.size());

		return candidateParenthesisPatterns;
	}

	private void report(CountMinSketch cms, Collection<? extends Map.Entry<String, Long>> patternToCount) {
		patternToCount.stream().sorted(Comparator.comparing(e -> -e.getValue())).limit(100).forEach(e -> {
			LOGGER.info("`{}` counted {}", HPUtils.encodeWhitespaceCharacters(e.getKey()), e.getValue());
		});

		Stream.of("*'''w'''", "&w;")
				.forEach(s -> LOGGER.info("Count '{}': {}",
						HPUtils.encodeWhitespaceCharacters(s),
						cms.getEstimatedCount(hashCode(s.codePoints().toArray()))));
	}

	private int setAndGet(CountMinSketch cms, int[] codePoints, int l, int s) {
		return cms.setAndGet(hashCode(codePoints, l, s));
	}

	private long hashCode(int[] codePoints) {
		return hashCode(codePoints, codePoints.length, 0);
	}

	// BEWARE This is a quick-n-dirty hash
	// see Arrays.hashCode
	private long hashCode(int[] codePoints, int l, int s) {
		if (codePoints == null)
			return 0;

		long result = 1;

		// BEWARE overflow
		for (int index = s; index - s < l; index++) {
			int element = codePoints[index];
			result = 31 * result + Murmur3.hash64(element);
		}

		return result;
	}

}
