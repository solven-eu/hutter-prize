package eu.solven.hutter_prize.reversible;

import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * This captures the idea a sentence generally starts with an upperCase. It is helpful to turns them into lowerCase to
 * simplify later autocompletion. It may also be useful by removing one concept from the final text. Not all such
 * upperCase should be simplified in lowerCase, for instance for proper nouns.
 * 
 * @author Benoit Lacelle
 *
 */
public class SentenceStartsWithUCPreprocessor extends AStringColumnEditorPreprocessor {
	// This is compatible with the normal word regex
	private static final String MAGIC_SUFFIX = "FLC";

	// `\v` catches any vertical newLine
	private AtomicLongMap<String> getProperNounToCount(String string) {
		AtomicLongMap<String> properNounToCount = AtomicLongMap.create();
		Pattern.compile("(?<=(?:[a-zA-Z] ))[A-Z][a-zA-Z]*")
				.matcher(string)
				.results()
				.map(MatchResult::group)
				// We exclude english `I` which is upperCase even in the middle of sentence
				// .filter(f -> !f.equals("I"))
				.forEach(mr -> properNounToCount.incrementAndGet(mr));

		// Register `I` as a properNoun, to keep it upperCase
		properNounToCount.incrementAndGet("I");

		return properNounToCount;
	}

	protected String compressString(Map<String, ?> context, int index, String string) {
		// AtomicLongMap<String> wordToCount = AtomicLongMap.create();
		// Pattern.compile("[a-zA-Z]+").matcher(string).results().forEach(mr ->
		// wordToCount.incrementAndGet(mr.group()));

		// if (string.contains("Asperger")) {
		// System.out.println(string);
		// }

		// A proper noun starts with upperCase without being start of sentence
		AtomicLongMap<String> properNounToCount = getProperNounToCount(string);

		String replaced = Pattern.compile("(?<=(\\. )|\\v ?|^)[a-zA-Z]+").matcher(string).replaceAll(mr -> {
			String firstWord = mr.group();

			if (properNounToCount.containsKey(firstWord)) {
				return firstWord;
			} else {

				if (firstWord.endsWith(MAGIC_SUFFIX)) {
					throw new IllegalArgumentException("TODO Escape words with improper case and ending with `LC`");
				}

				int firstCodepoint = firstWord.codePointAt(0);

				if (Character.isUpperCase(firstCodepoint)) {
					return Character.toString(Character.toLowerCase(firstCodepoint)) + firstWord.substring(1);
				}

				// if (firstWord.startsWith("image")) {
				// // System.out.println();
				// }

				// We expected an upperCase but it is not the case: we need to escape this exception
				return firstWord + MAGIC_SUFFIX;

			}
		});

		return replaced;
	}

	protected String decompressString(Map<String, ?> context, int index, String string) {
		AtomicLongMap<String> properNounToCount = getProperNounToCount(string);

		// if (string.contains("Asperger")) {
		// System.out.println();
		// }

		String replaced = Pattern.compile("(?<=(\\. )|\\v ?|^)[a-zA-Z]+").matcher(string).replaceAll(mr -> {
			String firstWord = mr.group();

			if (properNounToCount.containsKey(firstWord)) {

				// if (firstWord.startsWith("imageLC")) {
				// System.out.println();
				// }
				return firstWord;
			} else {
				int firstCodepoint = firstWord.codePointAt(0);

				// if (firstWord.startsWith("image")) {
				// System.out.println();
				// }

				if (Character.isUpperCase(firstCodepoint)) {
					// BEWARE Is this a bug?
					return firstWord;
				}

				if (!firstWord.endsWith(MAGIC_SUFFIX)) {
					return Character.toString(Character.toUpperCase(firstCodepoint)) + firstWord.substring(1);
				}

				return firstWord.substring(0, firstWord.length() - MAGIC_SUFFIX.length());
			}
		});

		return replaced;
	}
}
