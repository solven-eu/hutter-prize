package eu.solven.hutter_prize.kanzi_only;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import eu.solven.hutter_prize.reversible.utilities.AVisitingCompressor;

/**
 * Persist the whole input with Kanzi
 * 
 * @author Benoit Lacelle
 *
 */
public class KanziCompressor extends AVisitingCompressor<byte[], byte[]> {
	// From 0 to 9
	final int level;

	final boolean toSingleByteArray;

	public KanziCompressor(int level, boolean toSingleByteArray) {
		super(byte[].class, byte[].class);
		this.level = level;
		this.toSingleByteArray = toSingleByteArray;
	}

	public KanziCompressor(int level) {
		super(byte[].class, byte[].class);
		this.level = level;
		this.toSingleByteArray = true;
	}

	private Map<String, Object> kanziOptions() {
		Map<String, Object> commonOptions = new HashMap<>();
		// Best compression as we consider having unlimited time
		commonOptions.put("level", level);

		// From 0 to 3 (or more?)
		commonOptions.put("verbose", 0);
		return commonOptions;
	}

	@Override
	public byte[] defaultCompress(Object input) throws IOException {
		ByteArrayInputStream bytes = new ByteArrayInputStream((byte[]) input);

		Map<String, Object> commonOptions = kanziOptions();

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
	public byte[] defaultDecompress(Object output) throws IOException {
		ByteArrayInputStream compressed = new ByteArrayInputStream((byte[]) output);

		Map<String, Object> commonOptions = kanziOptions();

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

	@Override
	public Object compress(Object input) throws IOException {
		if (toSingleByteArray) {
			return defaultCompress(input);
		} else {
			return super.compress(input);
		}
	}

	@Override
	public Object decompress(Object input) throws IOException {
		if (toSingleByteArray) {
			return defaultDecompress(input);
		} else {
			return super.decompress(input);
		}
	}

	@Override
	protected byte[] compressString(byte[] string) throws IOException {
		return defaultCompress(string);
	}

	@Override
	protected byte[] decompressString(byte[] bytes) throws IOException {
		return defaultDecompress(bytes);
	}
}
