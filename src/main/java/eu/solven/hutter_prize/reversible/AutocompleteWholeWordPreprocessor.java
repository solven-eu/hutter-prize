package eu.solven.hutter_prize.reversible;

import java.util.ArrayList;
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
public class AutocompleteWholeWordPreprocessor extends ASymbolsPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(AutocompleteWholeWordPreprocessor.class);

	// Given we look for repeteting words, there is no need to look at the whole context: a relevant word would repeat
	// itself, and keep present at any position on the List of previous words
	private static final int PREVIOUS_WORDS_MAX = 128;

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		// Escape by doubling
		string = string.replaceAll("<", "<<").replaceAll(">", ">>");

		LinkedList<String> previousWords = new LinkedList<>();
		return Pattern.compile("\\w{3,}").matcher(string).replaceAll(mr -> {
			String word = mr.group();

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

				int commonPrefixLength = 0;
				int commonSuffixLength = 0;

				// Iterate forward in order to know what is the required prefix to find the same word i nthe previous
				// words
				while (iterator.hasNext()) {
					String intermediateWord = iterator.next();

					String commonPrefix = Strings.commonPrefix(word, intermediateWord);

					if (commonPrefix.length() >= 1) {
						commonPrefixLength = Math.max(commonPrefixLength, commonPrefix.length());
						// reduced = commonPrefix.length() + ">";
						// break;
					}

					String commonSuffix = Strings.commonSuffix(word, intermediateWord);

					if (commonSuffix.length() >= 1) {
						commonSuffixLength = Math.max(commonSuffixLength, commonSuffix.length());
						// reduced = "<" + commonSuffix.length();
						// break;
					}
				}

				int minimalLeftover = Math.min(commonSuffixLength, commonPrefixLength);
				// -1 as we pay for `>`
				int canSpare = word.length() - minimalLeftover - 1;

				if (canSpare > 0) {
					// +1 as we need a single char (at least) to find back the proper word
					int keptLength = minimalLeftover + 1;

					if (commonPrefixLength > commonSuffixLength) {
						// We prefer autocompleting by suffix
						reduced = "<" + word.substring(word.length() - keptLength, word.length());
					} else {
						reduced = word.substring(0, keptLength) + ">";
					}
				}
			}

			registerPreviousWord(previousWords, word);

			if (reduced.isEmpty()) {
				return word;
			} else {
				return reduced;
			}
		});

	}

	private void registerPreviousWord(LinkedList<String> previousWords, String word) {
		// Update the sliding window of N words
		if (previousWords.size() >= PREVIOUS_WORDS_MAX) {
			previousWords.removeFirst();
		}
		previousWords.add(word);
	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		LinkedList<String> previousWords = new LinkedList<>();

		// Escape by doubling

		String autocompleted = Pattern.compile("(<<?\\w+|\\w+>>?|\\w{3,})").matcher(string).replaceAll(mr -> {
			String word = mr.group();

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

				throw new IllegalStateException(
						"No valid autocompletion for " + word + " given previous words: " + previousWords);
			} finally {
				assert !completeWord.isEmpty();
				registerPreviousWord(previousWords, completeWord);
			}
		});

		return autocompleted.replaceAll("<<", "<").replaceAll(">>", ">");
	}

}
