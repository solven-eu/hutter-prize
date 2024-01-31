package eu.solven.hutter_prize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.reversible.ColumnRepresentation;
import eu.solven.hutter_prize.reversible.CompressColumns;
import eu.solven.hutter_prize.reversible.HeaderArticlesFooter;
import eu.solven.hutter_prize.reversible.ZipToByteArray;

public class HPCompressAndDecompress {
	private static final Logger LOGGER = LoggerFactory.getLogger(HPCompressAndDecompress.class);

	static final IReversibleCompressor compressor = new CompositeReversibleCompressor(Arrays.asList(
			// new ZipToByteArray(),
			new HeaderArticlesFooter(),
			new ColumnRepresentation(),
			new CompressColumns()));

	public static void main(String[] args) throws IOException {
		IReversibleCompressor compressors = HPCompressAndDecompress.compressor;

		Object initialInput = HPUtils.zipped;

		// We remove the initial ZIP impact from the analysis
		Object initialInputPreProcesses = new ZipToByteArray().compress(initialInput);

		Object compressed = compressors.compress(initialInputPreProcesses);
		LOGGER.info("{} compressed into {}",
				HPUtils.nameAndSize(initialInputPreProcesses),
				HPUtils.nameAndSize(compressed));

		Object decompressed = compressors.decompress(compressed);
		LOGGER.info("{} decompressed into {}", HPUtils.nameAndSize(compressed), HPUtils.nameAndSize(decompressed));

		String before = new String((byte[]) initialInputPreProcesses, StandardCharsets.UTF_8);
		String after = new String((byte[]) decompressed, StandardCharsets.UTF_8);
		for (int i = 0; i < before.length(); i++) {
			if (i > after.length()) {
				LOGGER.info("KO as AFTER is cut around {}", before.substring(i - 100, i + 100));
				break;
			}

			if (before.charAt(i) != after.charAt(i)) {
				int beforeKo = Math.max(0, i - 100);
				int afterKoBefore = Math.min(before.length(), i + 100);
				int afterKoAfter = Math.min(after.length(), i + 100);

				String commonPrefix = HPUtils.encodeWhitespaceCharacters(before.substring(beforeKo, i)) + "->";
				LOGGER.info("KO Before: {}",
						commonPrefix + HPUtils.encodeWhitespaceCharacters(
								before.charAt(i) + "<-" + before.substring(i + 1, afterKoBefore)));
				LOGGER.info("KO After:  {}",
						commonPrefix + HPUtils.encodeWhitespaceCharacters(
								after.charAt(i) + "<-" + after.substring(i + 1, afterKoAfter)));

				break;
			}
		}

		if (after.length() > before.length()) {
			LOGGER.info("KO After has an unexpected tail: {}",
					after.substring(before.length(), Math.min(before.length() + 100, after.length())));
		}
	}
}
