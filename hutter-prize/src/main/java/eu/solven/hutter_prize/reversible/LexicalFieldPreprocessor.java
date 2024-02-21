package eu.solven.hutter_prize.reversible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.util.concurrent.AtomicLongMap;

import eu.solven.hutter_prize.reversible.extract_language.PatternExtractorPreprocessor;

/**
 * We want to replace the lexical field of each page by some dumb word. The point being to have a simple text to
 * compress, and dedicated compression for the lexical field and for the generic text (left with grammar and
 * less-frequent specialized words).
 * 
 * @author Benoit Lacelle
 *
 */
public class LexicalFieldPreprocessor extends ASymbolsPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(LexicalFieldPreprocessor.class);

	private Map<String, String> getDictionary(Map<String, ?> context, int index) {
		Map<String, String> replaceThem;
		if (index >= 0) {
			replaceThem = ((List<Map<String, String>>) context.get("wordDictionaries")).get(index);
		} else {
			replaceThem = Collections.emptyMap();
		}
		return replaceThem;
	}

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		Map<String, String> replaceThem = getDictionary(context, index);

		return Phd9Preprocessor.replaceHC(replaceThem, string);
	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		Map<String, String> compressingReplaceThem = getDictionary(context, index);

		Map<String, String> decompressingReplaceThem = reverseReplaceThem(compressingReplaceThem);

		return Phd9Preprocessor.replaceHC(decompressingReplaceThem, string);
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

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		List<Map<String, String>> indexToDictionaries = new ArrayList<>();

		int listSize = list.size();
		for (int i = 0; i < listSize; i++) {
			Object o = list.get(i);

			Map<String, String> dictionary;
			if (o instanceof String) {
				String s = o.toString();

				Set<String> mathWordsAlreadyPresent =
						PatternExtractorPreprocessor.lookForExistingShortcuts(Pattern.compile("w\\d+_"), s);

				dictionary = new LinkedHashMap<>();

				MinMaxPriorityQueue<Map.Entry<String, Long>> pq = MinMaxPriorityQueue
						.orderedBy(Comparator.<Map.Entry<String, Long>, Long>comparing(e -> canSpare(e)).reversed())
						.maximumSize(100)
						.create();

				AtomicLongMap<String> fragmentToCount = AtomicLongMap.create();

				initBeforeGrowing(s, fragmentToCount);

				// int length = 1;
				while (true) {
					// for (int p = 0 ; p < s.length() - length ; p++) {
					//
					// }
					AtomicLongMap<String> nextFragmentToCount = AtomicLongMap.create();

					fragmentToCount.asMap()
							.entrySet()
							.stream()
							.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
							// This limit is dangerous, as some irrelevant fragment would pollute us like `the`
							.limit(100)
							.forEach(e -> {
								String subFragment = e.getKey();

								Pattern.compile("([ \\w]" + subFragment + "|" + subFragment + "[ \\w])")
										.matcher(s)
										.results()
										.forEach(mr -> nextFragmentToCount.incrementAndGet(mr.group()));
							});

					nextFragmentToCount.asMap()
							.entrySet()
							.stream()
							.sorted(Comparator.comparing(e -> -canSpare(e)))
							// .map(e -> Maps.immutableEntry(e.getKey(), e.getKey().length() * e.getValue()))
							.forEach(pq::add);

					nextFragmentToCount.asMap()
							.keySet()
							.stream()
							.filter(word -> pq.stream().noneMatch(topWord -> word.equals(topWord.getKey())))
							.forEach(nextFragmentToCount::remove);

					if (nextFragmentToCount.isEmpty()) {
						break;
					}

					// TODO Drop from `nextFragmentToCount` what has been rejected by pq
					fragmentToCount = nextFragmentToCount;
				}

				// // https://haifengl.github.io/nlp.html#bag-of-words
				// NGram[] gram = CooccurrenceKeywords.of(s, 10);

				AtomicInteger dicWordIndex = new AtomicInteger();
				// Stream.of(gram).flatMap(ng -> Stream.of(ng.words))
				pq.stream().map(e -> e.getKey()).filter(w -> w.length() >= 4).forEach(word -> {
					while (mathWordsAlreadyPresent.contains("w" + dicWordIndex.get() + "_")) {
						dicWordIndex.incrementAndGet();
					}

					String shortcut = "w" + dicWordIndex.getAndIncrement() + "_";
					dictionary.put(word, shortcut);
				});

			} else {
				dictionary = Collections.emptyMap();
			}

			indexToDictionaries.add(dictionary);
		}

		return Map.of("wordDictionaries", indexToDictionaries);
	}

	private void initBeforeGrowing(String s, AtomicLongMap<String> fragmentToCount) {
		Pattern.compile("[ \\w]{3}").matcher(s).results().forEach(mr -> fragmentToCount.incrementAndGet(mr.group()));
	}

	private long canSpare(Entry<String, Long> e) {
		return (e.getKey().length() - 3) * (e.getValue() - 1);
	}

}
