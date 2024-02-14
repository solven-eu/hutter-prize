package eu.solven.hutter_prize.kanzi_only;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.IReversibleCompressor;

/**
 * Persist the whole input with Kanzi
 * 
 * @author Benoit Lacelle
 *
 */
public class KanziCompressor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(KanziCompressor.class);

	final int level;

	public KanziCompressor(int level) {
		this.level = level;
	}

	@Override
	public Object compress(Object input) throws IOException {
		ByteArrayInputStream bytes = new ByteArrayInputStream((byte[]) input);

		Map<String, Object> commonOptions = new HashMap<>();
		// Best compression as we consider having unlimited time
		commonOptions.put("level", level);

		// From 0 to 3 (or more?)
		commonOptions.put("verbose", 0);

		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		{
			Map<String, Object> compressionOptions = new HashMap<>();

			compressionOptions.putAll(commonOptions);

			{
				InputStreamCompressor bc = new InputStreamCompressor(compressionOptions, bytes, compressed);

				int code = bc.call();

				if (code != 0) {
					bc.dispose();
				}
			}
		}
		return compressed.toByteArray();
	}

	@Override
	public Object decompress(Object output) throws IOException {
		ByteArrayInputStream compressed = new ByteArrayInputStream((byte[]) output);

		Map<String, Object> commonOptions = new HashMap<>();
		// Best compression as we consider having unlimited time
		commonOptions.put("level", 9);

		// From 0 to 3 (or more?)
		commonOptions.put("verbose", 0);

		ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
		{
			Map<String, Object> decompressionOptions = new HashMap<>();

			decompressionOptions.putAll(commonOptions);

			{
				InputStreamDecompressor bc =
						new InputStreamDecompressor(decompressionOptions, compressed, decompressed);

				int code = bc.call();

				if (code != 0) {
					bc.dispose();
				}
			}
		}
		return decompressed.toByteArray();
	}

}
