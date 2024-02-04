package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class CompositeReversibleCompressor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CompositeReversibleCompressor.class);

	final List<IReversibleCompressor> compressors;

	public CompositeReversibleCompressor(List<IReversibleCompressor> compressors) {
		super();
		this.compressors = compressors;
	}

	@Override
	public Object compress(Object input) throws IOException {
		Object output = input;

		for (IReversibleCompressor compressor : compressors) {
			Object newOutput = compressor.compress(output);

			LOGGER.info("{} compressed {} into {}",
					compressor.getClass().getSimpleName(),
					HPUtils.nameAndSize(output),
					HPUtils.nameAndSize(newOutput));
			output = newOutput;
		}

		return output;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		Object input = output;

		for (IReversibleCompressor compressor : Lists.reverse(compressors)) {
			Object newInput = compressor.decompress(input);

			LOGGER.info("{} uncompressed {} from {}",
					compressor.getClass().getSimpleName(),
					HPUtils.nameAndSize(newInput),
					HPUtils.nameAndSize(input));

			input = newInput;
		}

		return input;
	}

}
