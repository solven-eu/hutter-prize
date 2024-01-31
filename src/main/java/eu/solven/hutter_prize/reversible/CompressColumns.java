package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.util.Arrays;
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
		List<?> asList = (List<?>) input;

		String header = (String) asList.get(0);

		Map<String, List<?>> keyToVector = (Map<String, List<?>>) asList.get(1);
		String footer = (String) asList.get(2);

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

		return Arrays.asList(header, compressedKeyToVector, footer);
	}

	@Override
	public Object decompress(Object output) throws IOException {
		List<?> asList = (List<?>) output;

		String header = (String) asList.get(0);

		Map<String, List<?>> keyToVector = (Map<String, List<?>>) asList.get(1);
		String footer = (String) asList.get(2);

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

		return Arrays.asList(header, decompressedKeyToVector, footer);
	}

}
