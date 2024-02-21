package eu.solven.hutter_prize.reversible.utilities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.stream.Collectors;

import eu.solven.hutter_prize.IReversibleCompressor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

/**
 * Helps managing {@link IReversibleCompressor} handling different input types.
 * 
 * @author Benoit Lacelle
 *
 */
public abstract class AVisitingCompressor<S, T> implements IReversibleCompressor {
	final Class<S> classInput;
	final Class<T> classCompressed;

	public AVisitingCompressor(Class<S> classInput, Class<T> classCompressed) {
		this.classInput = classInput;
		this.classCompressed = classCompressed;
	}

	@Override
	public Object compress(Object input) throws IOException {
		if (input instanceof Map<?, ?> && !input.getClass().getName().startsWith("it.unimi.dsi.fastutil")) {
			Map<String, ?> asMap = (Map<String, ?>) input;

			// see eu.solven.hutter_prize.reversible.utilities.PersistingCompressor.persistMap(Path, Map<String,
			// Object>, BiConsumer<Path, byte[]>)
			if (asMap.isEmpty()) {
				return asMap;
			} else if (asMap.values().stream().allMatch(v -> v instanceof String)) {
				return defaultCompress(asMap);
			} else {
				return asMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), entry -> {
					try {
						return compress(entry.getValue());
					} catch (IOException e) {
						throw new UncheckedIOException("Issue with " + entry.getKey(), e);
					}
				}));
			}
		} else if (classInput.isInstance(input)) {
			return compressString(classInput.cast(input));
		} else {
			return defaultCompress(input);
		}
	}

	protected Object defaultCompress(Object input) throws IOException {
		return input;
	}

	protected abstract T compressString(S string) throws IOException;

	@Override
	public Object decompress(Object output) throws IOException {
		if (output instanceof Map<?, ?>) {
			Map<String, ?> asMap = (Map<String, ?>) output;

			return asMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), entry -> {
				try {
					return decompress(entry.getValue());
				} catch (IOException e) {
					throw new UncheckedIOException("Issue with " + entry.getKey(), e);
				}
			}));

		} else if (classCompressed.isInstance(output)) {
			return decompressString(classCompressed.cast(output));
		} else {
			return defaultDecompress(output);
		}
	}

	protected Object defaultDecompress(Object output) throws IOException {
		return output;
	}

	protected abstract S decompressString(T bytes) throws IOException;

}
