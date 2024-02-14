package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BaseVariableWidthVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.FixedWidthVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorLoader;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.VectorUnloader;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;
import com.google.common.collect.SetMultimap;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.IReversibleCompressor;

public class ArrowRepresentation implements IReversibleCompressor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArrowRepresentation.class);

	@Override
	public Object compress(Object input) throws IOException {
		List<String> strings = (List<String>) input;

		String pages = strings.get(1);
		String suffix = strings.get(2);

		String PAGE_PREFIX = "\n  <page>";
		int countPages = StringUtils.countOccurrencesOf(pages, PAGE_PREFIX);

		Schema schema = makeArrowSchema();

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

		try {
			// Buffer is not closed else it throws as VectorSchemaRoot is not closed
			BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);

			// VectorSchemaRoot is not closed as it is returned in some pipeline
			VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);

			root.getFieldVectors().forEach(fieldVector -> {
				if (fieldVector instanceof BaseVariableWidthVector) {
					((BaseVariableWidthVector) fieldVector).allocateNew(pages.length() * 8, countPages);
				} else if (fieldVector instanceof FixedWidthVector) {
					((FixedWidthVector) fieldVector).allocateNew(countPages);
				} else {
					fieldVector.allocateNew();
				}
			});
			// closerToColumn.values().stream().distinct().map(column -> root.getVector(column)).forEach(fieldVector ->
			// {
			// if (fieldVector instanceof BaseVariableWidthVector) {
			// ((BaseVariableWidthVector) fieldVector).allocateNew(countPages);
			// } else {
			// fieldVector.allocateNew();
			// }
			// });

			VarCharVector leftoverVector = (VarCharVector) root.getVector("leftover");
			leftoverVector.allocateNew(countPages);

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
						leftoverVector.setSafe(pageIndex, pageAfterPreviousValue.getBytes(StandardCharsets.UTF_8));
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
						leftoverVector.setSafe(pageIndex, pageAfterPreviousValue.getBytes(StandardCharsets.UTF_8));
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

					FieldVector vector = root.getVector(column);

					if (vector instanceof VarCharVector) {
						((VarCharVector) vector).set(pageIndex, value.getBytes(StandardCharsets.UTF_8));
					} else if (vector instanceof IntVector) {
						((IntVector) vector).set(pageIndex, Integer.parseInt(value));
					} else {
						throw new IllegalStateException(
								"Need to manage writing into " + vector.getClass().getSimpleName());
					}

					// We move after the value and not after the opener, as we may have multiple opener for a single
					// closer
					previousValuePosition += columnOpener.length() + value.length();
				}

				previousEndPage += previousValuePosition;
			}

			int finalPageIndex = pageIndex;
			closerToColumn.values().stream().distinct().map(column -> root.getVector(column)).forEach(fieldVector -> {
				fieldVector.setValueCount(finalPageIndex + 1);
			});
			root.setRowCount(finalPageIndex + 1);

			// System.out.print(root.contentToTSVString());
			VectorSchemaRoot theRoot = VectorSchemaRoot.create(root.getSchema(), allocator);

			return Arrays.asList(strings.get(0), root, suffix);
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

	/**
	 * We make a flat schema as we have issue managing a recursive schema (with Structs)
	 * 
	 * @return
	 */
	private Schema makeArrowSchema() {
		Field title = new Field("title", FieldType.notNullable(new ArrowType.Utf8()), null);
		Field id = new Field("id", FieldType.notNullable(new ArrowType.Int(32, true)), null);
		Field revisionId = new Field("revision_id", FieldType.notNullable(new ArrowType.Int(32, true)), null);
		Field revisionTs = new Field("revision_timestamp", FieldType.notNullable(new ArrowType.Utf8()), null);
		Field revisionUn = new Field("revision_username", FieldType.notNullable(new ArrowType.Utf8()), null);
		Field revisionControbutorId =
				new Field("revision_contributor_id", FieldType.notNullable(new ArrowType.Utf8()), null);

		Field text = new Field("text", FieldType.notNullable(new ArrowType.Utf8()), null);
		Field comment = new Field("comment", FieldType.notNullable(new ArrowType.Utf8()), null);

		// Field revision = new Field("revision",
		// FieldType.notNullable(new ArrowType.Struct()),
		// Arrays.asList(new Field("id", FieldType.notNullable(new ArrowType.Int(32, true)), null),
		// new Field("timestamp", FieldType.notNullable(new ArrowType.Int(32, true)), null),
		// new Field("contributor",
		// FieldType.notNullable(new ArrowType.Struct()),
		// Arrays.asList(
		// new Field("username", FieldType.notNullable(new ArrowType.Int(32, true)), null),
		// new Field("id", FieldType.notNullable(new ArrowType.Int(32, true)), null))),
		// new Field("text", FieldType.notNullable(new ArrowType.Utf8()), null)));
		// Used to hold the text from articles with a weird structure
		Field leftOver = new Field("leftover", FieldType.notNullable(new ArrowType.Utf8()), null);

		// Field separators = new Field("separators", FieldType.notNullable(new ArrowType.List()), null);
		Schema schema = new Schema(Arrays
				.asList(title, id, revisionId, revisionTs, revisionUn, revisionControbutorId, text, comment, leftOver));
		return schema;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
