package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.IReversibleCompressor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.lemire.integercompression.IntCompressor;

public class CompressColumns implements IReversibleCompressor {

	private static final Logger LOGGER = LoggerFactory.getLogger(CompressColumns.class);

	@Override
	public Object compress(Object input) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) input;

		Map<String, List<?>> keyToVector = (Map<String, List<?>>) asMap.get("keyToVector");

		IntCompressor iic = new IntCompressor();

		Map<String, List<?>> compressedKeyToVector = new LinkedHashMap<>();

		keyToVector.forEach((k, v) -> {
			if (v instanceof IntArrayList) {
				int[] data = ((IntArrayList) v).elements();
				int[] compressed = iic.compress(data);
				LOGGER.info("Compression {} ints into {}", data.length, compressed.length);

				compressedKeyToVector.put(k, IntArrayList.wrap(compressed));
			} else {
				compressedKeyToVector.put(k, v);
			}
		});

		// Make sure we let transit other information in other fields
		Map<String, Object> output = new LinkedHashMap<>(asMap);

		// Write an updated keyToVector
		output.put("keyToVector", compressedKeyToVector);

		return output;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) output;

		Map<String, List<?>> keyToVector = (Map<String, List<?>>) asMap.get("keyToVector");

		IntCompressor iic = new IntCompressor();

		Map<String, List<?>> decompressedKeyToVector = new LinkedHashMap<>();

		keyToVector.forEach((k, v) -> {
			if (v instanceof IntArrayList) {
				int[] data = ((IntArrayList) v).elements();
				int[] compressed = iic.uncompress(data);

				decompressedKeyToVector.put(k, IntArrayList.wrap(compressed));
			} else {
				decompressedKeyToVector.put(k, v);
			}
		});

		// Make sure we let transit other information in other fields
		Map<String, Object> input = new LinkedHashMap<>(asMap);

		// Write an updated keyToVector
		input.put("keyToVector", decompressedKeyToVector);

		return input;
	}

}
