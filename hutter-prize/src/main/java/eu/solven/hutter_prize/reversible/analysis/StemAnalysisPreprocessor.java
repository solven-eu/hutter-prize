package eu.solven.hutter_prize.reversible.analysis;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.reversible.AStringColumnEditorPreprocessor;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

/**
 * We analyze sequence of type of charactersBlocks.
 * 
 * @author Benoit Lacelle
 *
 */
public class StemAnalysisPreprocessor extends AStringColumnEditorPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(StemAnalysisPreprocessor.class);

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
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

		int depth = 2;
		int[][] transitions = new int[indexToRawPattern.size()][];
		for (int i = 0; i < indexToRawPattern.size(); i++) {
			transitions[i] = new int[indexToRawPattern.size()];
		}

		int listSize = list.size();
		for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {

			// Each char is attached to its int value
			Object rawInput = list.get(stringIndex);
			if (rawInput instanceof String) {
				String rawAsString = rawInput.toString();

				IntArrayFIFOQueue past = new IntArrayFIFOQueue();

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

						if (past.size() >= depth) {
							past.dequeueInt();
						}
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

			}
		}

		return Map.of();
	}

}
