package eu.solven.hutter_prize.reversible;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;
import com.google.common.collect.SetMultimap;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.IReversibleCompressor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public class ColumnRepresentation implements IReversibleCompressor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ColumnRepresentation.class);

	private Map<String, String> closerToColumn() {
		Map<String, String> closerToColumn = new HashMap<>();
		closerToColumn.put("</title>", "title");
		closerToColumn.put("</id>\n    <revision>", "id");
		closerToColumn.put("</id>\n    <restrictions>", "id");
		closerToColumn.put("</id>\n      <timestamp>", "revision_id");
		closerToColumn.put("</timestamp>", "revision_timestamp");
		closerToColumn.put("</username>", "revision_username");
		closerToColumn.put("</id>\n      </contributor>", "revision_contributor_id");
		closerToColumn.put("]]</text>\n    </revision>\n  </page>", "text");
		closerToColumn.put("</timestamp>", "revision_timestamp");
		closerToColumn.put("</restrictions>", "revision_restriction");
		closerToColumn.put("</comment>", "comment");
		closerToColumn.put("</text>\n    </revision>\n  </page>", "text");
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

		Map<String, List<?>> keyToVector = new HashMap<>();

		keyToVector.put("title", Arrays.asList(new String[countPages]));
		keyToVector.put("id", new IntArrayList(new int[countPages]));
		keyToVector.put("revision_id", new IntArrayList(new int[countPages]));
		keyToVector.put("revision_timestamp", Arrays.asList(new String[countPages]));
		keyToVector.put("revision_username", Arrays.asList(new String[countPages]));
		keyToVector.put("ip", Arrays.asList(new String[countPages]));
		keyToVector.put("revision_contributor_id", new IntArrayList(new int[countPages]));
		keyToVector.put("revision_restriction", Arrays.asList(new String[countPages]));
		keyToVector.put("comment", Arrays.asList(new String[countPages]));
		keyToVector.put("text", Arrays.asList(new String[countPages]));

		List<String> leftoverVector = Arrays.asList(new String[countPages]);
		keyToVector.put("leftovers", leftoverVector);

		List<List<String>> separators2 = Arrays.asList(new List[countPages]);
		keyToVector.put("separators", separators2);

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

					List<?> vector = keyToVector.get(column);

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

			// Make sure we let transit other information in other fields
			Map<String, Object> output = new LinkedHashMap<>(asMap);

			// We preprocessed `body` into `keyToVector`
			output.remove("body");
			output.put("keyToVector", keyToVector);

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

		Map<String, List<?>> keyToVector = (Map<String, List<?>>) asMap.get("keyToVector");

		StringBuilder sb = new StringBuilder();

		Map<String, String> closerToColumn = closerToColumn();

		List<String> leftovers = (List<String>) keyToVector.get("leftovers");
		List<List<String>> separators2 = (List<List<String>>) keyToVector.get("separators");

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
						sb.append(column.get(i).toString());
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
		input.remove("keyToVector");
		input.put("body", sb.toString());

		// Write an updated footer
		input.put("footer", footer);

		return input;
	}

}
