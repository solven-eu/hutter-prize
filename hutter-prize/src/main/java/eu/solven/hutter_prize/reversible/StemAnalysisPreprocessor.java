package eu.solven.hutter_prize.reversible;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.collections.impl.block.factory.Comparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicLongMap;

import eu.solven.hutter_prize.HPUtils;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

/**
 * We analyze word frequency to produce a relevant dictionary
 * 
 * @author Benoit Lacelle
 *
 */
public class StemAnalysisPreprocessor extends AStringColumnEditorPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(StemAnalysisPreprocessor.class);

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		AtomicLongMap<String> wordCount = AtomicLongMap.create();
		AtomicLongMap<String> wordPageCount = AtomicLongMap.create();

		List<String> desc = Arrays.asList("singleQuotes",
				"reference",
				"title",
				"curly",
				"proper",
				"weirdCase",
				"xml",
				"date",
				"words",
				"html_encoded",
				"whitespaces",
				"ellipsis",
				"sentences_markers",
				"eol",
				"XXX");

		// `Baron de Lahontan`
		List<String> indexToRawPattern = Arrays.asList("'{2}",
				"[\\[\\]]{2,}",
				"[\\{\\}]{2,}",
				"={2,}",
				"(de )?[A-Z][a-z]+([ \\-][A-Z][a-z]+)*(?![a-zA-Z])",
				"[a-zA-Z]*[A-Z][a-zA-Z]*",
				"</?\\w+>",
				"(?<!\\d[\\. ])\\d+(?![\\. ]\\d)",
				"\\w+(?:\\-\\w+)*",
				"'(&#\\d+;)+",
				" +",
				"\\.{3}",
				"[\\.,]",
				"\\v+",
				".");
		List<Pattern> patterns = indexToRawPattern.stream().map(Pattern::compile).collect(Collectors.toList());

		int[][] transitions = new int[indexToRawPattern.size()][];
		for (int i = 0; i < indexToRawPattern.size(); i++) {
			transitions[i] = new int[indexToRawPattern.size()];
		}

		int listSize = list.size();
		for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {
			AtomicLongMap<String> pageWordCount = AtomicLongMap.create();

			IntArrayFIFOQueue past = new IntArrayFIFOQueue();

			// Each char is attached to its int value
			Object rawInput = list.get(stringIndex);
			if (rawInput instanceof String) {
				String rawAsString = rawInput.toString();

				String joinedPattern = indexToRawPattern.stream().collect(Collectors.joining("|", "(", ")"));

				Pattern.compile(joinedPattern).matcher(rawAsString).results().forEach(mr -> {
					String group = mr.group();

					int matchingIndex = IntStream.range(0, patterns.size())
							.filter(i -> patterns.get(i).matcher(group).matches())
							.findFirst()
							.getAsInt();

					int previous;
					if (past.isEmpty()) {
						// We drop the border of the sequence
						previous = -1;
					} else {
						previous = past.lastInt();
					}
					past.enqueue(matchingIndex);

					if (previous >= 0) {
						transitions[previous][matchingIndex] += 1;
					}

					LOGGER.debug("`{}` -> {}", HPUtils.encodeWhitespaceCharacters(group), desc.get(matchingIndex));
				});

				for (int i = 0; i < transitions.length; i++) {
					String from = desc.get(i);

					int totalI = IntStream.of(transitions[i]).sum();

					if (totalI > 0) {
						for (int j = 0; j < transitions.length; j++) {
							String to = desc.get(j);

							int percent = 100 * transitions[i][j] / totalI;
							if (percent > 0) {
								LOGGER.info("From {} to {} has {}%", from, to, percent);
							}
						}
					}
				}

				// Either unit like `dB` or proper noun like `iPod`
				// Pattern.compile(" [a-z]+[A-Z]").matcher(rawAsString).results().forEach(mr -> {
				// LOGGER.info("{}", mr.group());
				//
				// // Words: encoded as lowerCase separated by space
				// // 26 * N + (N-1)*1whitespace : 27 symbols
				// // Words: encoded as lowerCase separated by first letter as upperCase
				// // 26 * N : 52 symbols
				//
				// // Proper noun:
				// // We would include symbols like `-` often used in firstNames
				// // We would also have various non-english symbols (e.g. with given accents)
				// });
				//
				// Pattern.compile("\\w+")
				// .matcher(rawAsString)
				// .results()
				// .map(mr -> mr.group().toLowerCase(Locale.US))
				// .forEach(word -> {
				// if (1 == pageWordCount.incrementAndGet(word)) {
				// // Count given word only on its first encounter in given page
				// wordPageCount.incrementAndGet(word);
				// }
				// wordCount.incrementAndGet(word);
				// });
			}
		}

		LOGGER.info("Cross pages frequent words");
		wordCount.asMap()
				.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Comparators.reverseNaturalOrder()))
				.limit(100)
				.forEach(e -> {
					LOGGER.info("{} -> {}", e.getKey(), e.getValue());
				});

		LOGGER.info("Distinct pages frequent words");
		wordPageCount.asMap()
				.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Comparators.reverseNaturalOrder()))
				.limit(100)
				.forEach(e -> {
					LOGGER.info("{} -> {}", e.getKey(), e.getValue());
				});

		LOGGER.info("Frequent on few pages");
		wordPageCount.asMap()
				.entrySet()
				.stream()
				// More frequent is better
				// Present on less pages is better
				.sorted(Comparator.comparing(e -> -(wordCount.get(e.getKey()) / (1 + wordPageCount.get(e.getKey())))))
				.limit(100)
				.forEach(e -> {
					LOGGER.info("{} -> count={} pages={}", e.getKey(), wordCount.get(e.getKey()), e.getValue());
				});

		return Map.of();
	}

}
