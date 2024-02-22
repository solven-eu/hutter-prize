package eu.solven.hutter_prize.kanzi_only;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

import eu.solven.hutter_prize.HPCompressAndDecompress;
import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.reversible.utilities.ZipToByteArray;
import kanzi.app.Kanzi;

/**
 * Compress and Decompress enwik8 like done with Kanzi only
 * 
 * @author Benoit Lacelle
 * @see Kanzi
 */
public class HPCompressAndDecompressKanzi {
	private static final Logger LOGGER = LoggerFactory.getLogger(HPCompressAndDecompressKanzi.class);

	private static final boolean CUSTOM_CLEAR_TXT = false;

	public static void main(String[] args) throws IOException {
		Object initialInput = HPUtils.zipped;

		byte[] original;
		if (CUSTOM_CLEAR_TXT) {
			String path = "/Users/blacelle/Downloads/enwik8_text";
			initialInput = new FileSystemResource(path);
			original = Files.readAllBytes(Paths.get(path));
		} else {
			// We remove the initial ZIP impact from the analysis
			original = (byte[]) new ZipToByteArray().compress(initialInput);
		}

		ByteArrayInputStream originalIs = new ByteArrayInputStream(original);

		Map<String, Object> commonOptions = new HashMap<>();
		// Best compression as we consider having unlimited time
		commonOptions.put("level", 9);

		// From 0 to 3 (or more?)
		commonOptions.put("verbose", 0);

		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		{
			Map<String, Object> compressionOptions = new HashMap<>();

			compressionOptions.putAll(commonOptions);

			{
				InputStreamCompressor bc = new InputStreamCompressor(compressionOptions, originalIs, compressed);

				int code = bc.call();

				if (code != 0) {
					bc.dispose();
				}
			}
		}

		LOGGER.info("{} compressed into {}",
				HPUtils.nameAndSize(original),
				HPUtils.nameAndSize(compressed.toByteArray()));

		ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
		{
			Map<String, Object> decompressionOptions = new HashMap<>();

			decompressionOptions.putAll(commonOptions);

			{
				InputStreamDecompressor bc = new InputStreamDecompressor(decompressionOptions,
						new ByteArrayInputStream(compressed.toByteArray()),
						decompressed);

				int code = bc.call();

				if (code != 0) {
					bc.dispose();
				}
			}
		}

		LOGGER.info("{} decompressed from {}",
				HPUtils.nameAndSize(decompressed.toByteArray()),
				HPUtils.nameAndSize(compressed.toByteArray()));

		HPCompressAndDecompress.sanityChecks(original, decompressed.toByteArray());
	}
}
