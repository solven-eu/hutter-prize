package eu.solven.hutter_prize.reversible.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.collections.impl.block.factory.Comparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicLongMap;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.reversible.AStringColumnEditorPreprocessor;

/**
 * We analyze character frequency. It helps knowing how far we are from an ACII only table, which would enable
 * byte-processing
 * 
 * @author Benoit Lacelle
 * @see WordAnalysisPreprocessor
 */
public class CharacterAnalysisPreprocessor extends AStringColumnEditorPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CharacterAnalysisPreprocessor.class);

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		AtomicLongMap<String> characterCount = AtomicLongMap.create();
		AtomicLongMap<String> characterPageCount = AtomicLongMap.create();

		int listSize = list.size();
		for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {
			AtomicLongMap<String> pageWordCount = AtomicLongMap.create();

			// Each char is attached to its int value
			Object rawInput = list.get(stringIndex);
			if (rawInput instanceof String) {
				String rawAsString = rawInput.toString();

				rawAsString.codePoints().mapToObj(codePoint -> Character.toString(codePoint)).forEach(character -> {

					if (1 == pageWordCount.incrementAndGet(character)) {
						// Count given word only on its first encounter in given page
						characterPageCount.incrementAndGet(character);
					}

					characterCount.incrementAndGet(character);
				});
			}
		}

		LOGGER.info("Number of characters: {}", characterCount.size());

		LOGGER.info("Cross pages frequent c");
		characterCount.asMap()
				.entrySet()
				.stream()
				.filter(e -> e.getKey().codePointAt(0) >= 0x007F)
				.sorted(Map.Entry.comparingByValue(Comparators.reverseNaturalOrder()))
				.limit(100)
				.forEach(e -> {
					LOGGER.info("{} -> {}", HPUtils.encodeWhitespaceCharacters(e.getKey()), e.getValue());
				});

		LOGGER.info("Distinct pages frequent words");
		characterPageCount.asMap()
				.entrySet()
				.stream()
				.filter(e -> e.getKey().codePointAt(0) >= 0x007F)
				.sorted(Map.Entry.comparingByValue(Comparators.reverseNaturalOrder()))
				.limit(100)
				.forEach(e -> {
					LOGGER.info("{} -> {}", HPUtils.encodeWhitespaceCharacters(e.getKey()), e.getValue());
				});

		LOGGER.info("Frequent on few pages");
		characterPageCount.asMap()
				.entrySet()
				.stream()
				.filter(e -> e.getKey().codePointAt(0) >= 0x007F)
				// More frequent is better
				// Present on less pages is better
				.sorted(Comparator
						.comparing(e -> -(characterCount.get(e.getKey()) / (1 + characterPageCount.get(e.getKey())))))
				.limit(100)
				.forEach(e -> {
					LOGGER.info("{} -> count={} pages={}",
							HPUtils.encodeWhitespaceCharacters(e.getKey()),
							characterCount.get(e.getKey()),
							e.getValue());
				});

		return Map.of();
	}

}
