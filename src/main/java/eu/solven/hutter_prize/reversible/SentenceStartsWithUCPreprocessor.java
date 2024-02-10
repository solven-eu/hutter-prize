package eu.solven.hutter_prize.reversible;

import java.util.Locale;
import java.util.Map;
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
public class SentenceStartsWithUCPreprocessor extends ASymbolsPreprocessor {
	private AtomicLongMap<String> getProperNounToCount(String string) {
		AtomicLongMap<String> properNounToCount = AtomicLongMap.create();
		Pattern.compile("(?<![\\n\\.] |\n|^)[A-Z][a-z]*")
				.matcher(string)
				.results()
				.forEach(mr -> properNounToCount.incrementAndGet(mr.group()));
		return properNounToCount;
	}

	protected String compressString(Map<String, ?> context, int index, String string) {
		// AtomicLongMap<String> wordToCount = AtomicLongMap.create();
		// Pattern.compile("[a-zA-Z]+").matcher(string).results().forEach(mr ->
		// wordToCount.incrementAndGet(mr.group()));

		// A proper noun starts with upperCase without being start of sentence
		AtomicLongMap<String> properNounToCount = getProperNounToCount(string);

		String replaced = Pattern.compile("(?<=([\n\\.] )|\n|^)[A-Z][a-zA-Z]*").matcher(string).replaceAll(mr -> {
			String firstWord = mr.group();

			if (properNounToCount.containsKey(firstWord)) {
				return firstWord;
			} else {

				if (firstWord.startsWith("image")) {
					// System.out.println();
				}

				return Character.toString(Character.toLowerCase(firstWord.codePointAt(0))) + firstWord.substring(1);
			}
		});

		return replaced;
	}

	protected String decompressString(Map<String, ?> context, int index, String string) {
		AtomicLongMap<String> properNounToCount = getProperNounToCount(string);

		String replaced = Pattern.compile("(?<=([\n\\.] )|\n|^)[a-z][a-zA-Z]*").matcher(string).replaceAll(mr -> {
			String firstWord = mr.group();

			if (properNounToCount.containsKey(firstWord)) {
				return firstWord;
			} else {
				if (firstWord.startsWith("image")) {
					System.out.println();
				}

				return Character.toString(Character.toUpperCase(firstWord.codePointAt(0))) + firstWord.substring(1);
			}
		});

		return replaced;
	}
}
