package eu.solven.hutter_prize.reversible;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator.OfInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicLongMap;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.reversible.analysis.CharactersBlockAnalysisPreprocessor;

/**
 * Like phd9 but more advanced patterns, and replacing with weirder characters
 * 
 * @author Benoit Lacelle
 * @see CharactersBlockAnalysisPreprocessor
 */
// https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h
public class Phd9AdvancedPreprocessor extends Phd9Preprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(Phd9AdvancedPreprocessor.class);

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		// We search for patterns like `&gt;` or `[a]`. We also looks for patterns of length 2 as it make it easy to
		// understand patterns like `[[`
		int minL = 2;
		int maxAdditionalLength = 8;

		Map<Integer, AtomicLongMap<String>> lengthToPatterns = new HashMap<>();

		AtomicLongMap<Integer> codePointToCount = analyzeCodePoints(list, s -> s);

		// ASCII before 32 are not printable
		OfInt availableCodePoints =
				IntStream.iterate(0, i -> i + 1).filter(codePoint -> 0 == codePointToCount.get(codePoint)
				// For a human, it is easier to read `{128}` than some weird and unusual symbol
				// && HPUtils.encodeWhitespaceCharacters(Character.toString(i)).length() == 1
				).peek(codePoint -> {
					LOGGER.info("{} ({}) is used as replacement",
							HPUtils.encodeWhitespaceCharacters(Character.toString(codePoint)),
							codePoint);
				}).iterator();

		Map<String, String> replaceThem = advancedHcReplaceThem(availableCodePoints);
		checkIsSafe(replaceThem);

		// This could be used to ensure it is safe to replace `&amp;` by `&`
		if (false) {
			AtomicLongMap<Integer> codePointToCountAfterHC = analyzeCodePoints(list, s -> replaceHC(replaceThem, s));
			LOGGER.info("We have {} `&`", codePointToCountAfterHC.get("&".codePointAt(0)));
		}

		if (false) {

			// We also looks for patterns like `*'''w'''`
			for (int patternLength = minL; patternLength <= minL + maxAdditionalLength; patternLength++) {
				LOGGER.info("Start length={}", patternLength);

				// We record all patterns: while it may grow very large, it is kind of limited as we will get ride of
				// words
				AtomicLongMap<String> patternToCount = AtomicLongMap.create();
				lengthToPatterns.put(patternLength, patternToCount);

				int listSize = list.size();
				for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {
					// Each char is attached to its int value
					Object rawInput = list.get(stringIndex);
					if (rawInput instanceof String) {
						String rawAsString = rawInput.toString();

						String asString = replaceHC(replaceThem, rawAsString);

						String simplified = compactWords(asString);

						int[] codePoints = simplified.codePoints().toArray();

						for (int s = 0; s < codePoints.length - patternLength; s++) {
							String pattern = simplified.substring(s, s + patternLength);
							if (canSpare(pattern, 1) > 0) {
								patternToCount.incrementAndGet(pattern);

								// if (pattern.contains("://")) {
								// int i = 0;
								// int j = i + i;
								// } else if (pattern.contains("<w>")) {
								// int i = 0;
								// int j = i + i;
								// }
							}
						}
					}
				}

				report(patternToCount.asMap().entrySet());
			}

			proposeNewPatterns(lengthToPatterns);
		}
		return Map.of("replaceThem", replaceThem);
	}

	public static Map<String, String> advancedHcReplaceThem(OfInt availableCodePoints) {
		Map<String, String> replaceThem = new LinkedHashMap<>();
		// https://fr.wikipedia.org/wiki/Mod%C3%A8le:Ref
		replaceThem.put("&lt;ref&gt;", Character.toString(availableCodePoints.nextInt()));
		replaceThem.put("&lt;/ref&gt;", Character.toString(availableCodePoints.nextInt()));

		// BEWARE It is ambiguous to accept ` ` as pattern lead|tail character, as it may be easy to foresee by some
		// predicator
		replaceThem.put(" [[", Character.toString(availableCodePoints.nextInt()));
		replaceThem.put("[[", Character.toString(availableCodePoints.nextInt()));
		replaceThem.put("]] ", Character.toString(availableCodePoints.nextInt()));
		replaceThem.put("]]", Character.toString(availableCodePoints.nextInt()));

		replaceThem.put(" ''", Character.toString(availableCodePoints.nextInt()));
		replaceThem.put("''", Character.toString(availableCodePoints.nextInt()));
		return replaceThem;
	}

	private AtomicLongMap<Integer> analyzeCodePoints(List<?> list, Function<String, String> preprocess) {
		AtomicLongMap<Integer> codePointToCount;
		codePointToCount = AtomicLongMap.create();
		{
			for (Object rawInput : list) {
				// Each char is attached to its int value
				if (rawInput instanceof String) {
					String asString = preprocess.apply(rawInput.toString());

					asString.codePoints().forEach(codePointToCount::incrementAndGet);
				}
			}
		}

		LOGGER.info("Total codePoints: {}", codePointToCount.sum());

		codePointToCount.asMap()
				.entrySet()
				.stream()
				.sorted(Comparator.comparing(e -> -e.getValue()))
				.limit(200)
				.forEach(e -> {
					int codePoint = e.getKey().intValue();
					LOGGER.info("Codepoint {} (`{}`) is printed {} times",
							codePoint,
							HPUtils.encodeWhitespaceCharacters(Character.toString(codePoint)),
							e.getValue());
				});

		// The following is useful to know which codePoints are going to be used as block markers
		// ASCII before 32 are not printable
		for (int i = 0; i < 512; i++) {
			if (0 == codePointToCount.get(i)
			// For a human, it is easier to read `{128}` than some weird and unusual symbol
			// && HPUtils.encodeWhitespaceCharacters(Character.toString(i)).length() == 1
			) {
				LOGGER.debug("{} ({}) is available", HPUtils.encodeWhitespaceCharacters(Character.toString(i)), i);
			}
		}

		return codePointToCount;
	}

	private Set<String> proposeNewPatterns(Map<Integer, AtomicLongMap<String>> lengthToPatterns) {
		Map<String, Long> top100 = new TreeMap<>();

		// TODO We should let `w` contribute into `z`, as a `w` can be catched by a soften `z`
		lengthToPatterns.values()
				.stream()
				.flatMap(am -> am.asMap().entrySet().stream())
				.sorted(Comparator.comparing(e -> {
					String symbol = e.getKey();
					long countOccurences = e.getValue();

					return -canSpare(symbol, countOccurences);
				}))
				.limit(100)
				.peek(e -> {
					LOGGER.info("`{}` has the potential to spare {} chars (count={})",
							HPUtils.encodeWhitespaceCharacters(e.getKey()),
							canSpare(e.getKey(), e.getValue()),
							e.getValue());
				})
				.forEach(e -> top100.put(e.getKey(), e.getValue()));

		return top100.keySet();
	}

	public static String compactWords(String asString) {
		String wordsAsWSentencesAsZ = simplifyWords(asString);
		String cleanSentences = compactSentences(wordsAsWSentencesAsZ);

		return cleanSentences;
	}

	private static String simplifyWords(String asString) {
		// Replace words as this preprocessor is focus on symbols
		// One string argument is we do not want to replace text like ` the ` in this preprocessor: they are
		// very common but would be better caught but a more english-specific preprocessor
		String simplified = Pattern.compile("\\w+( \\w+)*").matcher(asString).replaceAll(mr -> {
			String wordOrWords = mr.group();

			if (wordOrWords.indexOf(' ') < 0) {
				// 1 word
				return "w";
			} else {
				// multiple words
				return "z";
			}

		});
		return simplified;
	}

	private static String compactSentences(String asString) {
		String simplified = asString;
		while (true) {
			String before = simplified;
			String after = simplified;

			// Turns `z. z. z. ` into `z. `
			after = Pattern.compile("z([\\.,] z)+").matcher(after).replaceAll(mr -> {
				return "z";

			});

			// Turns `w, z` into `z`
			after = Pattern.compile("w([,] z)+").matcher(after).replaceAll(mr -> {
				return "z";

			});

			if (after.length() > before.length()) {
				throw new IllegalStateException("We grew `" + before + "` into `" + after + "`");
			} else if (after.length() == before.length()) {
				break;
			} else {
				// Continue trying to detect patterns
				simplified = after;
			}
		}
		return simplified;
	}

	public static long canSpare(String oldPattern, long countOccurences) {
		// Word and symbols can be replaced by word and symbol
		// long countSingleWord = CharMatcher.anyOf("w").countIn(oldPattern);
		// Sentence and symbols can be replaced by a sentences and 2 symbols
		// As `{{z}}z` can not be compacted into `#zz` as it would not be reversible
		// long countSentences = CharMatcher.anyOf("z").countIn(oldPattern);
		// long countOldSymbols = oldPattern.length() - countSingleWord - countSentences;

		// Initially true, as we will need to close current pattern
		boolean isOpen = false;
		boolean needOpening = true;
		boolean neeedClosing = true;

		StringBuilder proposedPatternBuilder = new StringBuilder(oldPattern.length());

		for (char c : oldPattern.toCharArray()) {
			if (c == 'w') {
				proposedPatternBuilder.append('w');
				if (!isOpen) {
					proposedPatternBuilder.append('#');
					isOpen = true;
				}
				needOpening = false;
				neeedClosing = false;
			} else if (c == 'z') {
				if (needOpening) {
					proposedPatternBuilder.append('(');
				}
				proposedPatternBuilder.append('z');
				proposedPatternBuilder.append(')');
				isOpen = true;
				neeedClosing = false;
			} else {
				// No need to write anything, except after the loop if there was only symbols
			}
		}

		if (neeedClosing) {
			proposedPatternBuilder.append('#');
		}

		// See unitTests: an optimal pattern need as many symbols than word groups
		// It is enough to know where to restore a prefix, suffix and separator
		// long countNewSymbols = Math.max(1, countWordGroups - 1);

		long sparePerOccurence = oldPattern.length() - proposedPatternBuilder.length();

		// May be negative if the pattern can not be efficiently compressed (e.g. `z w` is turned into `(z,w`)
		return countOccurences * sparePerOccurence;
	}

	private void report(Collection<? extends Map.Entry<String, Long>> patternToCount) {
		patternToCount.stream().sorted(Comparator.comparing(e -> -e.getValue())).limit(100).forEach(e -> {
			LOGGER.info("`{}` counted {}", HPUtils.encodeWhitespaceCharacters(e.getKey()), e.getValue());
		});
	}

}
