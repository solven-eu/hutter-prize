package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
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

public class ColumnRepresentation implements IReversibleCompressor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ColumnRepresentation.class);

	@Override
	public Object compress(Object input) throws IOException {
		List<String> strings = (List<String>) input;

		String pages = strings.get(1);
		String suffix = strings.get(2);

		String PAGE_PREFIX = "\n  <page>";
		int countPages = StringUtils.countOccurrencesOf(pages, PAGE_PREFIX);

		SetMultimap<String, String> openerToCloser = makeOpenerToCloser();

		Map<String, String> closerToColumn = new HashMap<>();
		closerToColumn.put("</title>\n    <id>", "title");
		closerToColumn.put("</id>\n    <revision>\n      <id>", "id");
		closerToColumn.put("</id>\n      <timestamp>", "revision_id");
		closerToColumn.put("</timestamp>\n      <contributor>\n        <username>", "revision_timestamp");
		closerToColumn.put("</username>\n        <id>", "revision_username");
		closerToColumn.put("</id>\n      </contributor>\n      <text xml:space=\"preserve\">#REDIRECT [[",
				"revision_contributor_id");
		closerToColumn.put("]]</text>\n    </revision>\n  </page>", "text");
		closerToColumn.put("</timestamp>\n      <contributor>\n        <username>", "revision_timestamp");
		closerToColumn.put("</id>\n      </contributor>\n      <minor />\n      <comment>", "revision_contributor_id");
		closerToColumn.put("</comment>\n      <text xml:space=\"preserve\">", "comment");
		closerToColumn.put("</text>\n    </revision>\n  </page>", "text");

		Map<String, List<?>> keyToVector = new HashMap<>();

		List<String> titles = Arrays.asList(new String[countPages]);
		keyToVector.put("title", titles);

		List<Integer> ids = new IntArrayList(new int[countPages]);
		keyToVector.put("id", ids);

		List<Integer> revision_id = new IntArrayList(new int[countPages]);
		keyToVector.put("revision_id", ids);

		List<String> revisionTs = Arrays.asList(new String[countPages]);
		keyToVector.put("revision_timestamp", revisionTs);

		List<String> revision_username = Arrays.asList(new String[countPages]);
		keyToVector.put("revision_username", revision_username);

		List<Integer> revision_contributor_id = new IntArrayList(new int[countPages]);
		keyToVector.put("revision_contributor_id", revision_contributor_id);

		List<String> comment = Arrays.asList(new String[countPages]);
		keyToVector.put("comment", comment);

		List<String> text = Arrays.asList(new String[countPages]);
		keyToVector.put("text", text);

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
					suffix = pages.substring(previousEndPage + 1) + suffix;

					break;
				}
				String PAGE_CLOSING = "  </page>";
				int indexEndPage = pages.indexOf(PAGE_CLOSING, indexStartPage);
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
						boolean startsWith = pageAfterPreviousValue.startsWith(prefix);

						if (!startsWith) {
							String candidate = pageAfterPreviousValue.substring(0,
									Math.min(pageAfterPreviousValue.length(), prefix.length()));
							LOGGER.debug("`{}` does not starts with `{}`",
									HPUtils.encodeWhitespaceCharacters(candidate),
									HPUtils.encodeWhitespaceCharacters(prefix));
						}

						return startsWith;
					}).findFirst();

					if (optColumnOpener.isEmpty()) {
						leftoverVector.set(pageIndex, pageAfterPreviousValue);
						previousValuePosition += pageAfterPreviousValue.length();
						break nextColumn;
					}

					String columnOpener = optColumnOpener.get();
					String pageAfterOpener = pageAfterPreviousValue.substring(columnOpener.length());

					Set<String> optClosers = openerToCloser.get(columnOpener);
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

					int closingIndex = pageAfterOpener.indexOf(optCloser.get());
					String value = pageAfterOpener.substring(0, closingIndex);

					String column = closerToColumn.entrySet()
							.stream()
							.filter(e -> optCloser.get().startsWith(e.getKey()))
							.map(e -> e.getValue())
							.findAny()
							.orElse(null);
					if (column == null) {
						throw new IllegalStateException(
								"We lack column for closer=`" + optCloser.get() + "` Page=" + page);
					}

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
					previousValuePosition += columnOpener.length() + value.length();
				}

				separators2.set(pageIndex, separators);
				previousEndPage += previousValuePosition;
			}

			return Arrays.asList(strings.get(0), keyToVector, suffix);
		} finally {
			LOGGER.info("Done");
		}
	}

	private SetMultimap<String, String> makeOpenerToCloser() {
		// Linked in order to put first the most probably prefix
		SetMultimap<String, String> openerToCloser = ListMultimapBuilder.linkedHashKeys().hashSetValues().build();

		openerToCloser.put("\n  <page>\n    <title>", "</title>\n    <id>");
		openerToCloser.put("</title>\n    <id>", "</id>\n    <revision>\n      <id>");
		openerToCloser.put("</id>\n    <revision>\n      <id>", "</id>\n      <timestamp>");
		openerToCloser.put("</id>\n      <timestamp>", "</timestamp>\n      <contributor>\n        <username>");
		openerToCloser.put("</timestamp>\n      <contributor>\n        <username>", "</username>\n        <id>");

		// There is an optional `<minor /><comment>`
		openerToCloser.put("</username>\n        <id>",
				"</id>\n      </contributor>\n      <minor />\n      <comment>");

		// There is redirects
		openerToCloser.put("</username>\n        <id>",
				"</id>\n      </contributor>\n      <text xml:space=\"preserve\">#REDIRECT [[");
		openerToCloser.put("</id>\n      </contributor>\n      <text xml:space=\"preserve\">#REDIRECT [[",
				"]]</text>\n    </revision>\n  </page>");

		openerToCloser.put("</id>\n      </contributor>\n      <minor />\n      <comment>",
				"</comment>\n      <text xml:space=\"preserve\">");
		openerToCloser.put("</comment>\n      <text xml:space=\"preserve\">", "</text>\n    </revision>\n  </page>");

		return openerToCloser;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		List<?> asList = (List<?>) output;

		String header = (String) asList.get(0);

		Map<String, List<?>> keyToVector = (Map<String, List<?>>) asList.get(1);
		String footer = (String) asList.get(2);

		// TODO Auto-generated method stub
		return null;
	}

}
