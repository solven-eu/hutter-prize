package eu.solven.hutter_prize.reversible.utilities;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.IReversibleCompressor;
import eu.solven.pepper.collection.PepperMapHelper;
import eu.solven.pepper.logging.PepperLogHelper;

public class CompositeReversibleCompressor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CompositeReversibleCompressor.class);

	final List<IReversibleCompressor> compressors;
	final boolean debug;

	public CompositeReversibleCompressor(List<IReversibleCompressor> compressors) {
		this.compressors = compressors;
		this.debug = false;
	}

	public CompositeReversibleCompressor(List<IReversibleCompressor> compressors, boolean debug) {
		this.compressors = compressors;
		this.debug = debug;
	}

	@Override
	public Object compress(Object input) throws IOException {
		Object output = input;

		for (IReversibleCompressor compressor : compressors) {
			Object newOutput = compress(output, compressor);

			output = newOutput;
		}

		return output;
	}

	private Object compress(Object output, IReversibleCompressor compressor) throws IOException {
		Object newOutput = compressor.compress(output);

		LOGGER.info("{} compressed {} into {} ({} into {})",
				compressor.getClass().getSimpleName(),
				PepperLogHelper.humanBytes(HPUtils.size(output)),
				PepperLogHelper.humanBytes(HPUtils.size(newOutput)),
				HPUtils.nameAndSize(output),
				HPUtils.nameAndSize(newOutput));

		if (debug) {
			Object decompressed;
			try {
				decompressed = compressor.decompress(newOutput);
			} catch (RuntimeException e) {
				throw new RuntimeException("Issue (de)compressing with " + compressor, e);
			}
			if (output instanceof byte[]) {
				if (!Arrays.equals((byte[]) decompressed, (byte[]) output)) {
					throw new IllegalStateException("Issue (de)compressing with " + compressor);
				}
			} else {
				// System.out.println(((Map<?,?>)output).keySet());

				if (!decompressed.equals(output)) {

					List<String> textLeft = PepperMapHelper.getRequiredAs(decompressed, "keyToVector", "text");
					List<String> textRight = PepperMapHelper.getRequiredAs(output, "keyToVector", "text");

					for (int i = 0; i < textLeft.size(); i++) {
						if (!textLeft.get(i).equals(textRight.get(i))) {
							throw new IllegalStateException("Issue (de)compressing with " + compressor);
						}
					}

					throw new IllegalStateException("Issue (de)compressing with " + compressor);
				}
			}
		}
		return newOutput;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		Object input = output;

		for (IReversibleCompressor compressor : Lists.reverse(compressors)) {
			Object newInput;
			try {
				newInput = compressor.decompress(input);
			} catch (RuntimeException e) {
				// Do not add the input in the message, as it is a representation of the whole input
				throw new IllegalArgumentException("Issue with " + compressor.getClass().getSimpleName(), e);
			}

			LOGGER.info("{} uncompressed {} from {} ({} from {})",
					compressor.getClass().getSimpleName(),
					PepperLogHelper.humanBytes(HPUtils.size(newInput)),
					PepperLogHelper.humanBytes(HPUtils.size(input)),
					HPUtils.nameAndSize(newInput),
					HPUtils.nameAndSize(input));

			input = newInput;
		}

		return input;
	}

}
