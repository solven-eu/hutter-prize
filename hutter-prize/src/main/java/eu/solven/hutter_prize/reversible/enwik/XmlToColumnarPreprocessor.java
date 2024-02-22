package eu.solven.hutter_prize.reversible.enwik;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;
import com.google.common.collect.SetMultimap;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.IReversibleCompressor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * This will detect the columnar representation of the underlying XML, and extract each data into separated columns
 * 
 * @author Benoit Lacelle
 *
 */
public class XmlToColumnarPreprocessor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(XmlToColumnarPreprocessor.class);

	public static final String KEY_KEYTOVECTOR = "ColumnRepresentation.keyToVector";
	public static final String KEY_TEXT = "text";

	private Map<String, String> closerToColumn() {
		Map<String, String> closerToColumn = new HashMap<>();
		closerToColumn.put("</title>", "title");
		closerToColumn.put("</id>\n    <revision>", "id");
		closerToColumn.put("</id>\n    <restrictions>", "id");
		closerToColumn.put("</id>\n      <timestamp>", "revision_id");
		closerToColumn.put("</timestamp>", "revision_timestamp");
		closerToColumn.put("</username>", "revision_username");
		closerToColumn.put("</id>\n      </contributor>", "revision_contributor_id");
		closerToColumn.put("]]</text>\n    </revision>\n  </page>", KEY_TEXT);
		closerToColumn.put("</timestamp>", "revision_timestamp");
		closerToColumn.put("</restrictions>", "revision_restriction");
		closerToColumn.put("</comment>", "comment");
		closerToColumn.put("</text>\n    </revision>\n  </page>", KEY_TEXT);
		closerToColumn.put("</ip>", "ip");
		return closerToColumn;
	}

	private SetMultimap<String, String> makeOpenerToCloser() {
		// Linked in order to put first the most probably prefix
		SetMultimap<String, String> openerToCloser = ListMultimapBuilder.linkedHashKeys().hashSetValues().build();

		openerToCloser.put("\n  <page>\n    <title>", "</title>");
		openerToCloser.put("</title>\n    <id>", "</id>\n    <revision>");
		openerToCloser.put("</title>\n    <id>", "</id>\n    <restrictions>");
		openerToCloser.put("</id>\n    <revision>\n      <id>", "</id>\n      <timestamp>");
		openerToCloser.put("</id>\n      <timestamp>", "</timestamp>");
		openerToCloser.put("</timestamp>\n      <contributor>\n        <username>", "</username>");

		openerToCloser.put("</username>\n        <id>", "</id>\n      </contributor>");

		openerToCloser.put("<restrictions>", "</restrictions>");

		openerToCloser.put("<comment>", "</comment>");
		openerToCloser.put("<text xml:space=\"preserve\">", "</text>\n    </revision>\n  </page>");
		openerToCloser.put("<text xml:space=\"preserve\">#REDIRECT [[", "]]</text>\n    </revision>\n  </page>");

		openerToCloser.put("<ip>", "</ip>");

		openerToCloser.put("<comment>", "</comment>");

		return openerToCloser;
	}

	@Override
	public Object compress(Object input) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) input;

		String pages = (String) asMap.get("body");
		String footer = (String) asMap.get("footer");

		String PAGE_PREFIX = "\n  <page>";
		int countPages = StringUtils.countOccurrencesOf(pages, PAGE_PREFIX);

		SetMultimap<String, String> openerToCloser = makeOpenerToCloser();

		Map<String, String> closerToColumn = closerToColumn();

		Map<String, Object> keyToVector = new HashMap<>();

		keyToVector.put("title", Arrays.asList(new String[countPages]));
		keyToVector.put("id", new IntArrayList(new int[countPages]));
		keyToVector.put("revision_id", new IntArrayList(new int[countPages]));
		keyToVector.put("revision_timestamp", Arrays.asList(new String[countPages]));
		keyToVector.put("revision_username", Arrays.asList(new String[countPages]));
		keyToVector.put("ip", Arrays.asList(new String[countPages]));
		keyToVector.put("revision_contributor_id", new IntArrayList(new int[countPages]));
		keyToVector.put("revision_restriction", Arrays.asList(new String[countPages]));
		keyToVector.put("comment", Arrays.asList(new String[countPages]));
		keyToVector.put(KEY_TEXT, Arrays.asList(new String[countPages]));

		List<String> leftoverVector = Arrays.asList(new String[countPages]);
		keyToVector.put("leftovers", leftoverVector);

		List<List<String>> separators2 = Arrays.asList(new List[countPages]);

		try {
			int pageIndex = -1;
			int previousEndPage = -1;
			nextPage: while (true) {
				pageIndex++;

				int indexStartPage = pages.indexOf(PAGE_PREFIX, previousEndPage + 1);
				if (indexStartPage < 0) {
					footer = pages.substring(previousEndPage + 1) + footer;

					break;
				}
				String PAGE_CLOSING = "  </page>";
				int indexEndPage = pages.indexOf(PAGE_CLOSING, indexStartPage + PAGE_PREFIX.length());
				if (indexEndPage < 0) {
					indexEndPage = pages.length();
				} else {
					indexEndPage += PAGE_CLOSING.length();
				}

				int previousValuePosition = 0;
				String page = pages.substring(indexStartPage, indexEndPage);

				List<String> separators = new ArrayList<>();

				nextColumn: while (previousValuePosition < page.length()) {
					// int finalPagePosition = pagePosition;

					String pageAfterPreviousValue = page.substring(previousValuePosition);

					Optional<String> optColumnOpener = openerToCloser.keySet().stream().filter(prefix -> {
						boolean startsWith = pageAfterPreviousValue.contains(prefix);

						if (!startsWith) {
							String candidate = pageAfterPreviousValue.substring(0,
									Math.min(pageAfterPreviousValue.length(), prefix.length()));
							LOGGER.debug("`{}` does not starts with `{}`",
									HPUtils.encodeWhitespaceCharacters(candidate),
									HPUtils.encodeWhitespaceCharacters(prefix));
						}

						return startsWith;
					})
							// Find the first appearing opener
							.sorted(Comparator.comparing(opener -> pageAfterPreviousValue.indexOf(opener)))
							.findFirst();

					if (optColumnOpener.isEmpty()) {
						// There is no opener
						if (closerToColumn.containsKey(pageAfterPreviousValue)) {
							// And the leftover is a closer
							separators.add(pageAfterPreviousValue);
						} else {
							if (pageAfterPreviousValue.length() >= 100) {
								System.out.println();
							}

							// But the leftover is unknown: we lack some structure detection
							leftoverVector.set(pageIndex, pageAfterPreviousValue);
						}

						previousValuePosition += pageAfterPreviousValue.length();
						break nextColumn;
					}

					String columnOpenerHint = optColumnOpener.get();
					String columnOpener = pageAfterPreviousValue.substring(0,
							pageAfterPreviousValue.indexOf(columnOpenerHint) + columnOpenerHint.length());
					String pageAfterOpener = pageAfterPreviousValue.substring(columnOpener.length());

					Set<String> optClosers = openerToCloser.get(columnOpenerHint);
					Optional<String> optCloser = optClosers.stream()
							.filter(closer -> pageAfterOpener.indexOf(closer) >= 0)
							.min(Comparator.comparing(closer -> pageAfterOpener.indexOf(closer)));

					if (optCloser.isEmpty()) {
						// throw new IllegalArgumentException("Page structure not-managed: " + System.lineSeparator() +
						// page);
						leftoverVector.set(pageIndex, pageAfterPreviousValue);
						previousValuePosition += pageAfterPreviousValue.length();
						break nextColumn;
					}

					separators.add(columnOpener);
					previousValuePosition += columnOpener.length();

					int closingIndex = pageAfterOpener.indexOf(optCloser.get());
					String value = pageAfterOpener.substring(0, closingIndex);

					String column = getCloserToColumn(closerToColumn, page, optCloser.get());

					List<?> vector = (List<?>) keyToVector.get(column);

					if (vector instanceof IntArrayList) {
						((IntArrayList) vector).set(pageIndex, Integer.parseInt(value));
					} else
					// if (vector instanceof IntVector)
					{
						((List<String>) vector).set(pageIndex, value);
					}
					// else {
					// throw new IllegalStateException(
					// "Need to manage writing into " + vector.getClass().getSimpleName());
					// }

					// We move after the value and not after the opener, as we may have multiple opener for a single
					// closer
					previousValuePosition += value.length();
				}

				separators2.set(pageIndex, separators);
				previousEndPage += previousValuePosition;

				// if (previousEndPage != indexEndPage) {
				// System.out.println();
				// }
			}

			{
				Object2IntMap<List<String>> separatorsToIndex = new Object2IntOpenHashMap<>();
				int[] indexes = new int[separators2.size()];

				for (int i = 0; i < indexes.length; i++) {
					int index = separatorsToIndex.computeIfAbsent(separators2.get(i), k -> separatorsToIndex.size());
					indexes[i] = index;
				}

				keyToVector.put("separators", Map.of("indexes", indexes, "mapping", separatorsToIndex));
			}

			// Make sure we let transit other information in other fields
			Map<String, Object> output = new LinkedHashMap<>(asMap);

			// We preprocessed `body` into `keyToVector`
			output.remove("body");
			output.put(KEY_KEYTOVECTOR, keyToVector);

			// Write an updated footer
			output.put("footer", footer);

			return output;
		} finally {
			LOGGER.info("Done");
		}
	}

	private String getCloserToColumn(Map<String, String> closerToColumn, String page, String closer) {
		if (closer == null) {
			throw new IllegalStateException("ouch");
		}

		String column = closerToColumn.entrySet()
				.stream()
				.filter(e -> closer.startsWith(e.getKey()))
				.map(e -> e.getValue())
				.findAny()
				.orElse(null);
		if (column == null) {
			throw new IllegalStateException("We lack column for closer=`" + closer + "` Page=" + page);
		}
		return column;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) output;

		String footer = (String) asMap.get("footer");

		Map<String, List<?>> keyToVector = (Map<String, List<?>>) asMap.get(KEY_KEYTOVECTOR);

		StringBuilder sb = new StringBuilder();

		Map<String, String> closerToColumn = closerToColumn();

		List<String> leftovers = (List<String>) keyToVector.get("leftovers");
		List<List<String>> separators2;
		{
			Map<String, Object> details = (Map<String, Object>) keyToVector.get("separators");

			int[] indexes = (int[]) details.get("indexes");
			Object2IntMap<List<String>> separatorsToIndex = (Object2IntMap<List<String>>) details.get("mapping");

			Int2ObjectMap<List<String>> indexToSeparators = new Int2ObjectOpenHashMap<>();
			separatorsToIndex.object2IntEntrySet().forEach(e -> indexToSeparators.put(e.getIntValue(), e.getKey()));

			separators2 = IntStream.of(indexes).mapToObj(i -> indexToSeparators.get(i)).collect(Collectors.toList());
		}

		for (int i = 0; i < keyToVector.get("id").size(); i++) {
			List<String> separators = separators2.get(i);

			for (int iSep = 0; iSep < separators.size(); iSep++) {
				String sep = separators.get(iSep);

				if (iSep >= 1) {
					String columnname = getCloserToColumn(closerToColumn, "", sep);

					List<?> column = keyToVector.get(columnname);
					if (column instanceof IntList) {
						sb.append(((IntList) column).getInt(i));
					} else {
						// We expect to receive only String
						Object columnValue = column.get(i);
						if (columnValue == null) {
							throw new IllegalStateException("column=" + columnname + " rowIndex=" + i);
						}
						sb.append(columnValue.toString());
					}
				}

				sb.append(sep);
			}

			String leftOver = leftovers.get(i);
			if (leftOver != null) {
				{

					String columnname = getCloserToColumn(closerToColumn, "", leftOver);

					List<?> column = keyToVector.get(columnname);
					if (column instanceof IntList) {
						sb.append(((IntList) column).getInt(i));
					} else {
						// We expect to receive only String
						sb.append(column.get(i).toString());
					}
				}
				sb.append(leftOver);
			}
		}

		// Make sure we let transit other information in other fields
		Map<String, Object> input = new LinkedHashMap<>(asMap);

		// We preprocessed `body` into `keyToVector`
		input.remove(KEY_KEYTOVECTOR);
		input.put("body", sb.toString());

		// Write an updated footer
		input.put("footer", footer);

		return input;
	}

}
