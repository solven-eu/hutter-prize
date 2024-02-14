package eu.solven.hutter_prize.reversible;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.collections.impl.block.factory.Comparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * We analyze word frequency to produce a relevant dictionary
 * 
 * @author Benoit Lacelle
 *
 */
public class WordAnalysisPreprocessor extends ASymbolsPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(WordAnalysisPreprocessor.class);

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		AtomicLongMap<String> wordCount = AtomicLongMap.create();
		AtomicLongMap<String> wordPageCount = AtomicLongMap.create();

		int listSize = list.size();
		for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {
			AtomicLongMap<String> pageWordCount = AtomicLongMap.create();

			// Each char is attached to its int value
			Object rawInput = list.get(stringIndex);
			if (rawInput instanceof String) {
				String rawAsString = rawInput.toString();

				Pattern.compile("\\w+")
						.matcher(rawAsString)
						.results()
						.map(mr -> mr.group().toLowerCase(Locale.US))
						.forEach(word -> {
							if (1 == pageWordCount.incrementAndGet(word)) {
								// Count given word only on its first encounter in given page
								wordPageCount.incrementAndGet(word);
							}
							wordCount.incrementAndGet(word);
						});
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
