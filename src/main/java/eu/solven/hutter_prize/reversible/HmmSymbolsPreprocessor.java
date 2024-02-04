package eu.solven.hutter_prize.reversible;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.hash.BloomFilter;

import eu.solven.hutter_prize.HPUtils;
import smile.sequence.HMM;

/**
 * Rely on a HMM to detect pattern. This is especially useful to detect symbolic patterns (e.g. `&amp;` which is
 * redundant in XML encoding HTML).
 * 
 * Once detected, such pattern will be replaced by a smaller pattern.
 * 
 * BEWARE: This is dropped at it is unclear how we can efficiently find back `&amp;` given a first-order HMM. Current
 * procedure go through the initial text to fetch all subStrings probability, which has bad performance.
 * 
 * @author Benoit Lacelle
 *
 */
// https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h
public class HmmSymbolsPreprocessor extends ASymbolsPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(HmmSymbolsPreprocessor.class);

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		BiMap<String, Integer> charToIndex = HashBiMap.create();

		charToIndex.put("[a-zA-Z]", 0);
		charToIndex.put("[0-9]", 1);

		// This is a slow sanity check
		{
			int[][] observations = new int[list.size()][];
			int[][] labels = new int[list.size()][];

			// Record the minimal observation, to prevent having observation having unnecessary high values
			int min = list.stream().map(s -> {
				if (s instanceof String) {
					return s.toString();
				} else {
					return null;
				}
			}).flatMapToInt(s -> s == null ? IntStream.empty() : s.codePoints()).min().getAsInt();

			for (int i = 0; i < list.size(); i++) {
				// Each char is attached to its int value
				Object rawInput = list.get(i);
				if (rawInput instanceof String) {
					String asString = rawInput.toString();
					observations[i] = asString.codePoints().map(cAsInt -> cAsInt - min).toArray();

					// The state/label is: are we looking at anyLetter, or anyDigit, or some symbol
					// Our goal is to detect symbolic patterns: some specific symbols, possibly related to any sort
					// (e.g. fixed or dynamic) word/letter
					// Typically, we'd like to catch `&amp;` and `[[someWord]]`
					labels[i] = asString.codePoints().map(cAsInt -> {
						char c = (char) cAsInt;

						if (CharMatcher.javaLetter().matches(c)) {
							return 0;
						} else if (CharMatcher.digit().matches(c)) {
							return 1;
						} else {
							String charAsString = Character.toString(c);
							int index = charToIndex.computeIfAbsent(charAsString, cc -> charToIndex.size());
							return index;
						}

					}).toArray();
				} else {
					// Smile does not accept an empty input. This would generate noise
					observations[i] = new int[] { 0 };
					labels[i] = new int[] { 0 };
				}
			}

			HMM hmm = HMM.fit(observations, labels);

			analyzeHmm(charToIndex, min, hmm, list);
		}

		return Collections.emptyMap();
	}

	private void analyzeHmm(BiMap<String, Integer> charToIndex, int min, HMM hmm, List<?> list) {
		// Using a bloomFilter, we may discard/not-try some entries due to a previous entry
		BloomFilter<int[]> processedSubString = BloomFilter.create(IntegerArrayFunnel.INSTANCE, 1000 * 1000);

		for (int l = 1; l <= 5; l++) {

			// This will keep in priority enties with highest probability
			MinMaxPriorityQueue<Map.Entry<String, Double>> stringToProbability =
					MinMaxPriorityQueue.<Map.Entry<String, Double>>orderedBy(Comparator.comparing(e -> -e.getValue()))
							.maximumSize(128)
							.create();

			final int length = l;

			// https://machinelearninginterview.com/topics/natural-language-processing/how-to-find-the-most-probable-sequence-of-tags-from-a-sequence-of-text/
			list.stream().filter(s -> s instanceof String).forEach(s -> {
				int[] observations = s.toString().codePoints().map(cAsInt -> cAsInt - min).toArray();

				int[] buffer = new int[length];
				for (int i = 0; i < observations.length - length; i++) {
					System.arraycopy(observations, i, buffer, 0, length);

					if (processedSubString.put(buffer)) {

						// https://stackoverflow.com/questions/28280721/java-8-streams-intstream-to-string
						String subString = IntStream.of(buffer)
								.map(c -> c + min)
								.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
								.toString();

						double probability = hmm.p(buffer);
						LOGGER.debug("{} -> {}", subString, probability);

						stringToProbability.add(Maps.immutableEntry(subString, probability));

						// int[] predicted = hmm.predict(knownRecurrentObservation);
						//
						// StringBuilder predictedSb = new StringBuilder();
						// for (int i = 0; i < predicted.length; i++) {
						// int label = predicted[i];
						//
						// if (label == 0) {
						// predictedSb.append('a');
						// } else if (label == 1) {
						// predictedSb.append('0');
						// } else {
						// predictedSb.append(charToIndex.inverse().get(label));
						// }
						// }

						// LOGGER.info("{} -> {}", subString, probability);
					}
				}

			});

			stringToProbability.stream().sorted(Comparator.comparing(e -> -e.getValue())).limit(30).forEach(e -> {
				LOGGER.info("{} -> {}", HPUtils.encodeWhitespaceCharacters(e.getKey()), e.getValue());
			});

		}

		// Given our HMM, we will re-run over the input, and report cases where the HMM report a
		// high-probability prediction, as it would mean current location is a frequent-pattern.
		String knownInput = "&amp";
		int[] knownRecurrentObservation = knownInput.chars().map(cAsInt -> cAsInt - min).toArray();
		double probabilityAmp = hmm.p(knownRecurrentObservation);
		LOGGER.info("{} -> {}", knownInput, probabilityAmp);

		int[] predicted = hmm.predict(knownRecurrentObservation);

		StringBuilder predictedSb = new StringBuilder();
		for (int i = 0; i < predicted.length; i++) {
			int label = predicted[i];

			if (label == 0) {
				predictedSb.append('a');
			} else if (label == 1) {
				predictedSb.append('0');
			} else {
				predictedSb.append(charToIndex.inverse().get(label));
			}
		}

		LOGGER.info("{} -> {}", knownInput, predictedSb);
	}
}
