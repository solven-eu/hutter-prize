package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import eu.solven.hutter_prize.IReversibleCompressor;
import eu.solven.pepper.collection.PepperMapHelper;

/**
 * 
 * @author Benoit Lacelle
 *
 */
public abstract class ASymbolsPreprocessor implements IReversibleCompressor {
	static interface IProcessString {
		String compress(Map<String, ?> context, int index, String string);
	}

	@Override
	public Object compress(Object input) throws IOException {
		return process(true, this::compressString, input, Optional.empty());
	}

	protected String compressString(Map<String, ?> context, int index, String string) {
		return string;
	}

	@Override
	public Object decompress(Object compressed) throws IOException {
		return process(false, this::decompressString, compressed, Optional.empty());
	}

	protected String decompressString(Map<String, ?> context, int index, String string) {
		return string;
	}

	protected Object process(boolean compressing,
			IProcessString transformString,
			Object compressed,
			Optional<Map<String, ?>> optContext) throws IOException {
		if (compressed instanceof String) {
			String string = (String) compressed;

			return transformString.compress(optContext.orElse(Map.of()), -1, string);
		} else if (compressed instanceof List<?>) {
			List<?> list = (List<?>) compressed;

			// Map<String, ?> compressingContext;
			if (optContext.isEmpty()) {
				// compressingContext =
				optContext = Optional.of(analyzeList(list));
				// } else {
				// compressingContext = list.ge
			}

			Map<String, ?> context = optContext.get();

			List<Object> output = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++) {
				Object o = list.get(i);
				Object c;
				if (o instanceof String) {
					try {
						c = transformString.compress(context, i, o.toString());
					} catch (RuntimeException e) {
						throw new IllegalStateException(
								"Issue (de)compressing index=" + i + " (" + this.getClass().getSimpleName() + ")",
								e);
					}
				} else {
					c = o;
				}

				output.add(c);
			}

			return output;
		} else if (compressed instanceof Map<?, ?>) {
			Map<String, ?> asMap = (Map<String, ?>) compressed;

			String contextKey = "keyToContext-" + this.getClass().getSimpleName();
			Map<String, Object> keyToContext;
			if (compressing) {
				keyToContext = new TreeMap<>();
			} else {
				keyToContext = PepperMapHelper.<Map<String, Object>>getOptionalAs(asMap, contextKey).orElse(Map.of());
			}

			if (asMap.containsKey("body")) {
				String body = asMap.get("body").toString();

				String simplifiedBody = transformString.compress(optContext.orElse(Map.of()), -1, body);

				// Make sure we let transit other information in other fields
				Map<String, Object> output = new LinkedHashMap<>(asMap);

				// We preprocessed body
				output.put("body", simplifiedBody);

				return output;
			} else if (asMap.containsKey("keyToVector")) {
				assert optContext.isEmpty();

				Map<String, ?> keyToVector = PepperMapHelper.getRequiredAs(asMap, "keyToVector");
				Map<String, Object> keyToVectorOutput = new LinkedHashMap<>(keyToVector);

				if (keyToVector.containsKey("text")) {
					List<String> texts = PepperMapHelper.getRequiredAs(keyToVector, "text");
					Map<String, ?> compressingContext =
							compressing ? analyzeList(texts) : (Map<String, ?>) keyToContext.get("text");
					List<?> processedTexts =
							(List<?>) process(compressing, transformString, texts, Optional.of(compressingContext));
					keyToVectorOutput.put("text", processedTexts);
					if (compressing) {
						keyToContext.put("text", compressingContext);
					}
				}

				// https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h#L168
				// For timestamps
				if (keyToVector.containsKey("timestamp")) {
					List<String> timestamps = PepperMapHelper.getRequiredAs(keyToVector, "timestamp");
					Map<String, ?> compressingContext =
							compressing ? analyzeList(timestamps) : (Map<String, ?>) keyToContext.get("timestamp");
					List<String> processedTimestamps = (List<String>) process(compressing,
							transformString,
							timestamps,
							Optional.of(compressingContext));
					keyToVectorOutput.put("timestamp", processedTimestamps);
					if (compressing) {
						keyToContext.put("timestamp", compressingContext);
					}
				}

				Map<String, Object> decompressed = new LinkedHashMap<>(asMap);
				decompressed.put("keyToVector", keyToVectorOutput);

				if (compressing) {
					if (!keyToContext.isEmpty()) {
						decompressed.put(contextKey, keyToContext);
					}
				} else {
					decompressed.remove(contextKey);
				}

				return decompressed;
			} else {
				return compressed;
			}
		} else {
			return compressed;
		}
	}

	protected Map<String, ?> analyzeList(List<?> list) {
		return Collections.emptyMap();
	}

}
