package eu.solven.hutter_prize.deprecated;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.google.common.math.Stats;
import com.google.common.util.concurrent.AtomicLongMap;

import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.CompleteLinkage;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.Matrix;

public class HutterPrizeCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(HutterPrizeCompressor.class);

	private static final boolean LIMIT_CACHE_COMPUTING_SEP_OCCURENCES = false;

	public static void main(String[] args) throws IOException {
		// http://mattmahoney.net/dc/enwik8.zip
		// http://mattmahoney.net/dc/enwik9.zip
		Resource zipped = new FileSystemResource("/Users/blacelle/workspace3/enwik8.zip");

		try (ZipInputStream zipInputStream = new ZipInputStream(zipped.getInputStream())) {
			ZipEntry nextEntry;

			while (null != (nextEntry = zipInputStream.getNextEntry())) {
				LOGGER.info(" Entry: {}", nextEntry.getName());

				byte[] bytes = zipInputStream.readAllBytes();

				// byte[] bytesPrefix = Arrays.copyOf(bytes, 10 * 1024);
				//
				// System.out.println(new String(bytesPrefix, StandardCharsets.UTF_8));

				String inputAsString = new String(bytes, StandardCharsets.UTF_8);

				// https://stackoverflow.com/questions/805679/gzip-compression-did-not-work-good-for-a-data-of-64k
				// https://superuser.com/questions/479074/why-doesnt-gzip-compression-eliminate-duplicate-chunks-of-data
				// 3GZip works on block of 32KB: Should we do the same?

				String first32KBlock = inputAsString.substring(0, 4 * 32 * 1024);
				System.out.println(first32KBlock);

				List<AtomicLongMap<String>> separatorLengthToCandidatesToOccurences =
						separatorAnalysis(first32KBlock, s -> true);

				stripIrrelevantSeparators(separatorLengthToCandidatesToOccurences);
				stripRedundantSeparators(separatorLengthToCandidatesToOccurences);
				finalPrintSeparators(separatorLengthToCandidatesToOccurences);

				computeStats(first32KBlock, separatorLengthToCandidatesToOccurences);

				if (LIMIT_CACHE_COMPUTING_SEP_OCCURENCES) {

					// At this step, `separatorLengthToCandidatesToOccurences` gives a good indicator of relevant
					// separators
					// However, it is flawed as the initial scan for separator was not perfect (e.g. we relied on
					// features
					// discarding seemingly poor separator, which may have discarded good separators while they have not
					// yet
					// encountered often enough to be considered good)
					// We will then redo a full separator analysis, but limited to the separator (or their parent)
					// qualified
					// as good in the first step: it would help detecting good parent separators

					Set<String> allSeparators = new HashSet<>();
					separatorAnalysis(first32KBlock, separator -> {
						allSeparators.add(separator);
						// Prevent any computation as we just want to spot separators
						return false;
					});
					LOGGER.info("There is {} total separators", allSeparators.size());

					allSeparators.removeIf(separator -> {
						int length = separator.length();

						// We accept child separator with up to 3 less characters
						// This will prevent single-char separators with common letter (e.g. `e`) to match
						// everything
						int minLength = Math.max(1, length - 2);

						Optional<String> optChildren = IntStream.rangeClosed(minLength, length)
								// -1 to match the index in a 0-based List
								.map(i -> i - 1)
								.mapToObj(i -> separatorLengthToCandidatesToOccurences.get(i))
								.flatMap(l -> l.asMap().keySet().stream())
								.filter(children -> separator.contains(children))
								.findAny();

						if (optChildren.isPresent()) {
							// This separator has a children which has been considered valid: the new separator may
							// be valid
							return false;
						}

						// Remove this irrelevant separator
						return true;
					});
					LOGGER.info("There is {} total separators after filtering from passV1", allSeparators.size());

					List<AtomicLongMap<String>> separatorLengthToCandidatesToOccurencesV2 =
							separatorAnalysis(first32KBlock, allSeparators::contains);

					stripIrrelevantSeparators(separatorLengthToCandidatesToOccurencesV2);
					stripRedundantSeparators(separatorLengthToCandidatesToOccurencesV2);

					finalPrintSeparators(separatorLengthToCandidatesToOccurencesV2);

				}
			}
		}
	}

	private static void computeStats(String first32kBlock,
			List<AtomicLongMap<String>> separatorLengthToCandidatesToOccurences) {
		// We compute stats per separator, as we expect separators of a common tabular structure to have similar
		// properties
		// Typically, each row of a CSV file has roughly the same length, and each record of an XML file has similar
		// markup

		Map<String, Stats> separatorToStats = new LinkedHashMap<>();

		separatorLengthToCandidatesToOccurences.stream()
				.flatMap(m -> m.asMap().keySet().stream())
				.forEach(separator -> {
					assert !separator.isEmpty();

					int position = -1;

					List<Integer> positions = new ArrayList<>();
					while ((position = first32kBlock.indexOf(separator, position)) >= 0) {
						positions.add(position);

						position += separator.length();
					}

					int[] arrayPositions = positions.stream().mapToInt(Integer::intValue).toArray();
					separatorToStats.put(separator, Stats.of(arrayPositions));
				});

		// separatorToStats.forEach((k, v) -> LOGGER.info("{} -> {}", k, v));

		separatorToStats.entrySet().stream().sorted(Comparator.comparing(e -> {
			// Negative for ordering from higher score to lower
			return -computeScore(e);
		}))
				.limit(20)
				.forEach(s -> LOGGER.info("Separator=`{}` score={} stats={}",
						encodeWhitespaceCharacters(s.getKey()),
						(int) computeScore(s),
						s.getValue()));

		// try clustering
		if (false) {
			double[][] indexToFeatures = new double[separatorToStats.size()][];

			// We want to cluster the separators, to capture the different tabular structure
			// We may have some structure earlier in the input, and another structure later in the input
			// We may also have some deeper tabular structure embedded in some outer tabulart structure (e.g. some array
			// appearing for each record of an external list of entries)
			{
				AtomicInteger index = new AtomicInteger();
				separatorToStats.values().forEach(s -> {
					// count: given a tabular structure, multiple separator would have similar count (e.g. in XML and
					// CSV)
					// min: given a tabular structure, all related separators should first appear around the first entry
					// max: given a tabular structure, all related separators should last appear around the last entry
					// mean: given a tabular structure, all related separators should have similar distance between
					// entries
					// variance: given a tabular structure, all related separators should have similar variance of
					// entries
					// width
					indexToFeatures[index.getAndIncrement()] =
							new double[] { s.count(), s.min(), s.max(), s.mean(), s.populationVariance() };
				});
			}

			Distance<double[]> d = new EuclideanDistance();

			Matrix proximity = d.D(indexToFeatures).standardize();

			HierarchicalClustering clusters = HierarchicalClustering.fit(CompleteLinkage.of(proximity.toArray()));

			// We want to spot the main structure (e.g. each article) in a cluster
			// And the rest in another cluster
			int nbPartitions = 2;
			int[] partitions = clusters.partition(nbPartitions);

			// System.out.println(Arrays.toString(partitions));

			IntStream.range(0, nbPartitions).forEach(partitionIndex -> {
				LOGGER.info("Partition index: {}", partitionIndex);

				AtomicInteger index = new AtomicInteger();
				separatorToStats.entrySet().forEach(s -> {
					int currentIndex = index.getAndIncrement();

					if (partitions[currentIndex] == partitionIndex) {
						LOGGER.info("`{}` {}", encodeWhitespaceCharacters(s.getKey()), s.getValue());
					}
				});
			});
			// var y = clusters.partition(6);
			// ScatterPlot.of(x, y, '.', Palette.COLORS).window();
		}
	}

	private static double computeScore(Entry<String, Stats> e) {
		Stats stats = e.getValue();

		// long count = stats.count();

		double score = 1D;

		// We prefer lower standardDev, as each entry should have similar size
		score /= stats.populationStandardDeviation();

		// We want as much text being covered
		score *= (stats.max() - stats.min());

		// We are happy if the separator is wider and numerous
		// BEWARE How does this behave on CSV with 1-char separator?
		// We log the count, as some character are very numerous (like `e` in FR)
		score *= (e.getKey().length() * Math.log(stats.count()));
		return score;
	}

	private static void finalPrintSeparators(List<AtomicLongMap<String>> separatorLengthToCandidatesToOccurences) {
		IntStream.range(0, separatorLengthToCandidatesToOccurences.size()).forEach(separatorLengthIndex -> {
			AtomicLongMap<String> separatorToOccurence =
					separatorLengthToCandidatesToOccurences.get(separatorLengthIndex);

			int separatorLength = separatorLengthIndex + 1;
			LOGGER.info("Separator length: {}", separatorLength);

			separatorToOccurence.asMap()
					.entrySet()
					.stream()
					// Sot from greater to smaller
					.sorted(Comparator.comparing(e -> -e.getValue()))
					.limit(30)
					.forEach(e -> {
						String separator = e.getKey();

						String humanFriendlySeparator = encodeWhitespaceCharacters(separator);

						LOGGER.info("Potential separator: `{}` (occurences={})", humanFriendlySeparator, e.getValue());
					});
		});
	}

	private static void stripIrrelevantSeparators(List<AtomicLongMap<String>> separatorLengthToCandidatesToOccurences) {
		separatorLengthToCandidatesToOccurences.forEach(sepToCount -> {
			// A separator appearing only once is not a separator: it is just any string which has been considered, but
			// did not accumulate
			dropSepWithNOccurences(sepToCount, 1);
		});
	}

	/**
	 * Given a separator looking like `</column2><column3>` with 5 occurrences, we would also spot `</column2><column3`
	 * and `/column2><column3>` with at least 5 occurrences. If the sub-separator has the same number of occurrences,
	 * then they can definitely be dropped.
	 * 
	 * @param separatorLengthToCandidatesToOccurences
	 */
	private static void stripRedundantSeparators(List<AtomicLongMap<String>> separatorLengthToCandidatesToOccurences) {
		long countBefore = separatorLengthToCandidatesToOccurences.stream().mapToLong(AtomicLongMap::size).sum();

		// For each separator, we will check if there is a parent with the same occurrences
		for (int i = 0; i < separatorLengthToCandidatesToOccurences.size() - 1; i++) {
			LOGGER.info("Stripping separators with length={}", i + 1);
			AtomicLongMap<String> shorter = separatorLengthToCandidatesToOccurences.get(i);
			AtomicLongMap<String> longer = separatorLengthToCandidatesToOccurences.get(i + 1);

			Set<String> shortSepToDrop = new HashSet<>();

			shorter.asMap().forEach((shortSep, shortOcc) -> {
				Optional<Entry<String, Long>> coveringParent = longer.asMap()
						.entrySet()
						.stream()
						.filter(e -> e.getValue().equals(shortOcc))
						.filter(e -> e.getKey().contains(shortSep))
						.findAny();

				if (coveringParent.isPresent()) {
					long occurences = separatorLengthToCandidatesToOccurences.get(shortSep.length() - 1).get(shortSep);
					LOGGER.debug("Dropping separator: `{}` (occurences={}). Parent is `{}`",
							encodeWhitespaceCharacters(shortSep),
							occurences,
							encodeWhitespaceCharacters(coveringParent.get().getKey()));
					shortSepToDrop.add(shortSep);
				}
			});

			shortSepToDrop.forEach(shorter::remove);
		}

		long countAfter = separatorLengthToCandidatesToOccurences.stream().mapToLong(AtomicLongMap::size).sum();
		LOGGER.info("Separator candidate before stripped from {} to {}", countBefore, countAfter);
	}

	private static String encodeWhitespaceCharacters(String separator) {
		// if (separator.contains("\r") || separator.contains("\n")) {
		// System.out.println();
		// }

		String humanFriendlySeparator;
		// https://stackoverflow.com/questions/4731055/whitespace-matching-regex-java
		String whitespacesRegex = "\\s+";
		// https://stackoverflow.com/questions/19737653/what-is-the-equivalent-of-regex-replace-with-function-evaluation-in-java-7
		{
			Pattern p = Pattern.compile(whitespacesRegex);
			Matcher m = p.matcher(separator);
			StringBuilder sb = new StringBuilder();
			while (m.find()) {
				String group = m.group();
				String sanitizedWhitespace = group
						// ` ` is replaced by a placeholder, to be restored later
						.replace(' ', '_')
						.replace("\r", Matcher.quoteReplacement("\\r"))
						.replace("\n", Matcher.quoteReplacement("\\n"))
						.replaceAll(whitespacesRegex, "[ ]")
						.replace('_', ' ');
				m.appendReplacement(sb, sanitizedWhitespace);
			}
			m.appendTail(sb);
			humanFriendlySeparator = sb.toString();
		}
		return humanFriendlySeparator;
	}

	private static List<AtomicLongMap<String>> separatorAnalysis(String inputAsString,
			Predicate<String> isPotentialSeparator) {
		List<AtomicLongMap<String>> separatorLengthToCandidatesToOccurences = new ArrayList<>();

		for (int separatorLength = 1; separatorLength < 64; separatorLength++) {
			AtomicLongMap<String> separatorToOccurence = AtomicLongMap.create();
			separatorLengthToCandidatesToOccurences.add(separatorToOccurence);

			// This counting methodology has flows, as it would count 3 `aba` in `abababa` instead of 2
			for (int beforeSepIndex = 0; beforeSepIndex + separatorLength < inputAsString.length(); beforeSepIndex++) {
				String separator = inputAsString.substring(beforeSepIndex, beforeSepIndex + separatorLength);

				if (!isPotentialSeparator.test(separator)) {
					LOGGER.debug("This separator is considered as not relevant");
					continue;
				}

				// if (separator.equals(" form of government ")) {
				// System.out.println();
				// }

				long newCount = separatorToOccurence.incrementAndGet(separator);

				if (LIMIT_CACHE_COMPUTING_SEP_OCCURENCES) {
					// This may be premature optimization
					if (newCount == 1) {
						// This is a new separator
						if (separatorToOccurence.size() >= 1024) {
							// We have too many candidates: strip the less relevant ones
							// BEWARE This may strip away any recently discovered prefix
							stripSeparator(separatorToOccurence);
						}
					}
				}
			}
			LOGGER.info("We are done with separatorLength={}", separatorLength);
		}

		return separatorLengthToCandidatesToOccurences;
	}

	private static void stripSeparator(AtomicLongMap<String> separatorToOccurence) {
		long sizeBeforeStrip = separatorToOccurence.size();
		// We have too many different separators: let's drop the rare ones
		// separatorToOccurence.

		do {
			// We drop all separator with the minimal encounter ratio
			long occurencesToDrop =
					separatorToOccurence.asMap().values().stream().mapToLong(Long::longValue).min().getAsLong();
			dropSepWithNOccurences(separatorToOccurence, occurencesToDrop);
		}
		// Repeat until we made enough room for new separators
		while (separatorToOccurence.size() >= 1024 / 2);

		long sizeAfterStrip = separatorToOccurence.size();

		LOGGER.debug("We stripped from {} to {} separators", sizeBeforeStrip, sizeAfterStrip);
	}

	private static void dropSepWithNOccurences(AtomicLongMap<String> separatorToOccurence, long occurencesToDrop) {
		LOGGER.debug("We drop separator with occurences ={}", occurencesToDrop);

		separatorToOccurence.asMap()
				.entrySet()
				.stream()
				.filter(e -> e.getValue().longValue() == occurencesToDrop)
				.forEach(e -> {
					String separatorToDrop = e.getKey();

					// if (separatorToDrop.equals("namespace")) {
					// System.out.println();
					// }

					separatorToOccurence.remove(separatorToDrop);
				});
	}
}
