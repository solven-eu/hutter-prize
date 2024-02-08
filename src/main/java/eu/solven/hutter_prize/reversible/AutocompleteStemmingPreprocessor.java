package eu.solven.hutter_prize.reversible;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;

import smile.nlp.stemmer.LancasterStemmer;

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
public class AutocompleteStemmingPreprocessor extends ASymbolsPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(AutocompleteStemmingPreprocessor.class);

	// Given we look for repeteting words, there is no need to look at the whole context: a relevant word would repeat
	// itself, and keep present at any position on the List of previous words
	private static final int PREVIOUS_STEMS_MAX = 16;

	// https://www.cs.toronto.edu/~frank/csc2501/Readings/R2_Porter/Porter-1980.pdf
	final LancasterStemmer stemmer = new LancasterStemmer();

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		// Escape by doubling
		string = string.replaceAll("<", "<<").replaceAll(">", ">>");

		// AtomicLongMap<String> stemToCount = AtomicLongMap.create();

		Matcher matcher = Pattern.compile("[a-zA-Z]{3,}").matcher(string);

		// First pass: we detect the frequent stems
		// {
		// matcher.results().forEach(mr -> {
		// String word = mr.group();
		//
		// String stem = stem(word);
		// stemToCount.incrementAndGet(stem);
		// });
		// }

		// LinkedList<String> previousWords = new LinkedList<>();
		LinkedList<String> previousStems = new LinkedList<>();
		return matcher.replaceAll(mr -> {
			String word = mr.group();

			// https://www.cs.toronto.edu/~frank/csc2501/Readings/R2_Porter/Porter-1980.pdf
			String stem = stem(word);

			if (!isWorkableStem(word, stem)) {
				return word;
			}

			// if (stemToCount.get(stem) <= 2) {
			// // We have a nice stem, but it is barely used
			// // registerPreviousWord(previousStems, word);
			// return word;
			// }

			// int foundIndex = -1;
			ListIterator<String> iterator = previousStems.listIterator(previousStems.size());

			String reduced = "";

			// Iterate backward for the next previous occurrence of the same word
			while (iterator.hasPrevious()) {
				String previousStem = iterator.previous();

				// String previousStem = stem(previousWord);

				if (stem.equals(previousStem)) {
					int currentIndex = iterator.previousIndex() + 1;

					int foundIndex = previousStems.size() - 1 - currentIndex;

					reduced = ">" + foundIndex + word.substring(stem.length());
					break;
				}
				// continue looking for the same word in previous context
			}

			registerPreviousWord(previousStems, stem);

			if (reduced.isEmpty()) {
				return word;
			} else {
				return reduced;
			}
		});

	}

	private boolean isWorkableStem(String word, String stem) {
		if (stem.equals(word)) {
			// registerPreviousWord(previousStems, word);
			// These word may be compressed by AutocompletePreprocessor
			return false;
		} else if (!word.startsWith(stem)) {
			// The rest of this algorithm assumes words starts by their stem

			return false;
		} else {
			return true;
		}
	}

	// We may need to apply multiple times stemming for words like `encyclopedia`
	private String stem(String word) {
		String stem = stemmer.stem(word);

		// `anarchy` -> `anarch` hence catching `anarchists`
		if (stem.length() >= 2 && stem.endsWith("y")) {
			stem = stem.substring(0, stem.length() - "y".length());
		}

		if (Character.isUpperCase(word.charAt(0)) && word.toLowerCase(Locale.US).startsWith(stem)) {
			stem = Character.toUpperCase(word.charAt(0)) + stem.substring(1);
		}

		return stem;
	}

	private void registerPreviousWord(LinkedList<String> previousWords, String word) {
		// Update the sliding window of N words
		if (previousWords.size() >= PREVIOUS_STEMS_MAX) {
			previousWords.removeFirst();
		}
		previousWords.add(word);
	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		LinkedList<String> previousStems = new LinkedList<>();

		// Escape by doubling

		String autocompleted = Pattern.compile("(>?>(\\d+)[a-zA-Z]+|[a-zA-Z]{3,})").matcher(string).replaceAll(mr -> {
			String word = mr.group();

			if (word.startsWith(">>")) {
				// This is an escaped autocomplete symbol
				return word;
			}

			// String completeWord = word;

			String stem = "";
			String autocompletedWord = word;
			try {
				if (!word.startsWith(">")) {
					stem = stem(word);
					return word;
				}

				String stemIndexAsString = mr.group(2);
				int stemIndex = Integer.parseInt(stemIndexAsString);

				stem = previousStems.get(previousStems.size() - stemIndex - 1);

				autocompletedWord = stem + word.substring(">".length() + stemIndexAsString.length());
				return autocompletedWord;
			} finally {
				// assert !stem.isEmpty();
				if (isWorkableStem(autocompletedWord, stem)) {
					registerPreviousWord(previousStems, stem);
				}
			}
		});

		return autocompleted.replaceAll("<<", "<").replaceAll(">>", ">");
	}

}
