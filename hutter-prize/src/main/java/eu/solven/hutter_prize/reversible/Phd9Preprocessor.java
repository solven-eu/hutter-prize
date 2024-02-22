package eu.solven.hutter_prize.reversible;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.HPUtils;

/**
 * PHD9 was Hutter Prize winner in 2018, and some of its logic is still used by latest top-entries. We re-implement here
 * related and improved logics.
 * 
 * @author Benoit Lacelle
 *
 */
// https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h
public class Phd9Preprocessor extends AStringColumnEditorPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(Phd9Preprocessor.class);

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		Map<String, String> replaceThem = (Map<String, String>) context.get("replaceThem");

		return replaceHC(replaceThem, string);
	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		Map<String, String> compressingReplaceThem = (Map<String, String>) context.get("replaceThem");

		Map<String, String> decompressingReplaceThem = reverseReplaceThem(compressingReplaceThem);

		return replaceHC(decompressingReplaceThem, string);
	}

	public static Map<String, String> reverseReplaceThem(Map<String, String> compressingReplaceThem) {
		// We reverse key and value, and iteration order
		Map<String, String> decompressingReplaceThem = new LinkedHashMap<>();

		List<Map.Entry<String, String>> compressingEntries = new ArrayList<>(compressingReplaceThem.entrySet());
		// Reverse iteration order
		Collections.reverse(compressingEntries);
		// Reverse key and values
		compressingEntries.forEach(e -> decompressingReplaceThem.put(e.getValue(), e.getKey()));
		return decompressingReplaceThem;
	}

	public static String replaceHC(Map<String, String> replaceThem, String asString) {
		Map<String, String> appliedPatterns = new LinkedHashMap<>();

		for (Entry<String, String> e : replaceThem.entrySet()) {
			String rawOldPatterm = e.getKey();
			// We need to apply previous patterns to next patterns to handle patterns impacting each-others (e.g. `[[`
			// and ` [[`)
			// String cleanOldPattern = replaceHC(appliedPatterns, rawOldPatterm);
			// if (!cleanOldPattern.equals(rawOldPatterm)) {
			// throw new IllegalStateException("We do not handle recursive patterns: Please re-order around `"
			// + HPUtils.encodeWhitespaceCharacters(rawOldPatterm)
			// + "` and `"
			// + HPUtils.encodeWhitespaceCharacters(cleanOldPattern)
			// + "`");
			// }

			String newPattern = e.getValue();

			asString = asString.replaceAll(Pattern.quote(rawOldPatterm), newPattern);

			appliedPatterns.put(rawOldPatterm, newPattern);
		}
		return asString;
	}

	public static void checkIsSafe(Map<String, String> replaceThem) {
		Map<String, String> appliedPatterns = new LinkedHashMap<>();

		for (Entry<String, String> e : replaceThem.entrySet()) {
			appliedPatterns.values()
					.stream()
					.filter(previousProducedValue -> e.getKey().contains(previousProducedValue))
					.findAny()
					.ifPresent(producedThenConsumed -> {
						throw new IllegalArgumentException(
								HPUtils.encodeWhitespaceCharacters(producedThenConsumed) + " is producedThenConsumed");
					});

			appliedPatterns.put(e.getKey(), e.getValue());

		}
	}

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		Map<String, String> replaceThem = hcReplaceThem();
		checkIsSafe(replaceThem);

		return Map.of("replaceThem", replaceThem);
	}

	public static Map<String, String> hcReplaceThem() {
		Map<String, String> replaceThem = new LinkedHashMap<>();

		replaceThem.put("&lt;", "<");
		replaceThem.put("&gt;", ">");
		replaceThem.put("&quot;", "\"");
		// `&amp;` is last to prevent conflicts
		replaceThem.put("&amp;", "&");

		return replaceThem;
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
