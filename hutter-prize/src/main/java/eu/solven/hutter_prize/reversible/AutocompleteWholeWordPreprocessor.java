package eu.solven.hutter_prize.reversible;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/**
 * This preprocessor will leave symbol indicated we should autocomplete to the right, or to the left. The autocompletion
 * could be based on diverse engines. A simple one is to search a previous occurence of a word starting similarly, and
 * autocomplete by reproducing the previous similar word. In case of autocomplete error, we would not write the
 * autocomplete symbol.
 * 
 * One advantage is to be locally: we could have cases where autocomplete does not work: we would just skip them by not
 * writing the autocomplete symbol.
 * 
 * @author Benoit Lacelle
 *
 */
public class AutocompleteWholeWordPreprocessor extends AStringColumnEditorPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(AutocompleteWholeWordPreprocessor.class);

	private static final boolean DEBUG = true;

	// Given we look for repeteting words, there is no need to look at the whole context: a relevant word would repeat
	// itself, and keep present at any position on the List of previous words
	private static final int PREVIOUS_WORDS_MAX = 1024;

	private final int previousWordMax;

	public AutocompleteWholeWordPreprocessor(int previousWordMax) {
		assert previousWordMax >= 1;
		this.previousWordMax = previousWordMax;
	}

	public AutocompleteWholeWordPreprocessor() {
		this(PREVIOUS_WORDS_MAX);
	}

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		if (index == 296) {
			// System.out.println(string);
		}

		// Escape by doubling
		String escaped = string.replaceAll("<(?=\\p{IsLatin})", "<<").replaceAll("(?<=\\p{IsLatin})>", ">>");

		LinkedList<String> previousWords = new LinkedList<>();
		// https://stackoverflow.com/questions/899422/regular-expression-for-a-string-that-does-not-start-with-a-sequence
		// We have issues with negative look-behind as it correctly rejects `<<some`, but it accepts `<some`
		String compressed = Pattern.compile("(?<![\\p{IsLatin}<]{2})\\p{IsLatin}{3,}(?![\\p{IsLatin}>]{2})")
				.matcher(escaped)
				.replaceAll(mr -> {
					String word = mr.group();

					// if (DEBUG && word.equals("com")) {
					// System.out.println();
					// }

					// TODO Use a Trie https://commons.apache.org/proper/commons-collections/apidocs/org/apache/commons/collections4/Trie.html
					int foundIndex = -1;
					ListIterator<String> iterator = previousWords.listIterator(previousWords.size());

					// Iterate backward for the next previous occurrence of the same word
					while (iterator.hasPrevious()) {
						String previousWord = iterator.previous();

						if (previousWord.equals(word)) {
							foundIndex = iterator.previousIndex() + 1;
							break;
						}
						// continue looking for the same word in previous context
					}

					String reduced = "";
					if (foundIndex >= 0) {
						// WHile we switch from backward to forward, we need to skip current item
						assert iterator.nextIndex() == foundIndex;
						iterator.next();

						int intermediateCommonPrefixLength = 0;
						int intermediateCommonSuffixLength = 0;

						// Iterate forward in order to know what is the required prefix to find the same word in the
						// previous
						// words
						while (iterator.hasNext()) {
							String intermediateWord = iterator.next();

							String commonPrefix = Strings.commonPrefix(word, intermediateWord);

							if (commonPrefix.length() >= 1) {
								intermediateCommonPrefixLength =
										Math.max(intermediateCommonPrefixLength, commonPrefix.length());
								// reduced = commonPrefix.length() + ">";
								// break;
							}

							String commonSuffix = Strings.commonSuffix(word, intermediateWord);

							if (commonSuffix.length() >= 1) {
								intermediateCommonSuffixLength =
										Math.max(intermediateCommonSuffixLength, commonSuffix.length());
								// reduced = "<" + commonSuffix.length();
								// break;
							}

							int keptLength = keptLength(intermediateCommonPrefixLength, intermediateCommonSuffixLength);
							if (!isUseful(word, keptLength)) {
								// We have at best 1 char spared: it is pointless as we will ose 1 char in `>` or `<`
								break;
							}
						}

						int keptLength = keptLength(intermediateCommonPrefixLength, intermediateCommonSuffixLength);

						if (isUseful(word, keptLength)) {

							if (intermediateCommonPrefixLength > intermediateCommonSuffixLength) {
								// We prefer autocompleting by suffix
								reduced = "<" + word.substring(word.length() - keptLength, word.length());
							} else {
								reduced = word.substring(0, keptLength) + ">";
							}
						}
					}

					if (DEBUG && !reduced.isEmpty()) {
						// System.out.println(reduced + " given " + previousWords);
					}

					registerPreviousWord(previousWords, word);

					if (reduced.isEmpty()) {
						return word;
					} else {
						return reduced;
					}
				});

		if (DEBUG) {
			assert decompressString(context, index, compressed).equals(string);
		}
		return compressed;

	}

	private int keptLength(int intermediateCommonPrefixLength, int intermediateCommonSuffixLength) {
		// We minimize between either autocompleting left or autocompleting right
		int minimalLeftover = Math.min(intermediateCommonSuffixLength, intermediateCommonPrefixLength);

		// +1 as we need a single char (at least) to find back the proper word
		int keptLength = minimalLeftover + 1;

		return keptLength;
	}

	private boolean isUseful(String word, int keptLength) {

		// + 1 as we pay for `<` or `>`
		return keptLength + 1 < word.length();
	}

	private void registerPreviousWord(LinkedList<String> previousWords, String word) {
		boolean removed = previousWords.remove(word);

		if (removed) {
			// It is moved forward
		} else {
			// Update the sliding window of N words
			if (previousWords.size() >= previousWordMax) {
				previousWords.removeFirst();
			}
		}

		previousWords.add(word);
	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		LinkedList<String> previousWords = new LinkedList<>();

		// The 3rd block wants to catch simple words: we have to ensure it does not catch `word` in `word>>`, so we
		// forbid both `\w` and `>` in the lookahead|behinds
		String autocompleted = Pattern.compile(
				"((?<!<)<\\p{IsLatin}+|\\p{IsLatin}+>(?!>)|(?<![<\\p{IsLatin}])\\p{IsLatin}{3,}(?![>\\p{IsLatin}]))")
				.matcher(string)
				.replaceAll(mr -> {
					String word = mr.group();

					if (word.contains("_")) {
						// System.out.println(word);
					}

					String completeWord = word;

					try {
						if (!word.startsWith("<") && !word.endsWith(">")) {
							// This is an escaped autocomplete symbol
							return word;
						} else if (word.startsWith("<<") || word.endsWith(">>")) {
							// This is an escaped autocomplete symbol
							return word;
						}

						if (word.endsWith(">")) {
							String commonPrefix = word.substring(0, word.length() - 1);

							Iterator<String> iterator = previousWords.descendingIterator();
							while (iterator.hasNext()) {
								String previousWord = iterator.next();

								if (previousWord.startsWith(commonPrefix)) {
									completeWord = previousWord;
									return previousWord;
								}

							}
						}
						if (word.startsWith("<")) {
							String commonSuffix = word.substring(1);

							Iterator<String> iterator = previousWords.descendingIterator();
							while (iterator.hasNext()) {
								String previousWord = iterator.next();

								if (previousWord.endsWith(commonSuffix)) {
									completeWord = previousWord;
									return previousWord;
								}

							}
						}

						LOGGER.warn("Issue decompressing: {}{}", System.lineSeparator(), string);
						throw new IllegalStateException(
								"No valid autocompletion for `" + word + "` given previous words: " + previousWords);
					} finally {
						if (DEBUG && !completeWord.equals(word)) {
							// System.out.println(completeWord + " given " + previousWords);
						}

						assert !completeWord.isEmpty();

						// if (completeWord.startsWith("<<")) {
						// System.out.println();
						// }
						registerPreviousWord(previousWords, completeWord);
					}
				});

		String unescaped = autocompleted.replaceAll("<<(?=\\p{IsLatin})", "<").replaceAll("(?<=\\p{IsLatin})>>", ">");
		return unescaped;
	}

}
