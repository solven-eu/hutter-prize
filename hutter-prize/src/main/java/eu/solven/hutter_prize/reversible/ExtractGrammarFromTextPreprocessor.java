package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import eu.solven.hutter_prize.IReversibleCompressor;

/**
 * Turns `Je m'appele Benoit` into `Ww'wP` and `Je\nm\nappele\nBenoit`
 * 
 * One may argue such approach are not optimal as they break the structure of the text, by splitting in along different
 * canals. Each canal may be easier to compress. But the whole is more difficult to compress.
 * 
 * @author Benoit Lacelle
 *
 */
public class ExtractGrammarFromTextPreprocessor implements IReversibleCompressor {

	@Override
	public Object compress(Object input) throws IOException {
		if (input instanceof String) {
			return compressString(input.toString());
		} else if (!(input instanceof Map)) {
			return input;
		}
		Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) input);

		Map<String, ?> vectors = (Map<String, ?>) map.get(ColumnRepresentation.KEY_KEYTOVECTOR);

		Map<String, Object> newVectors = new LinkedHashMap<>(vectors);
		map.put(ColumnRepresentation.KEY_KEYTOVECTOR, newVectors);

		List<String> texts = (List<String>) newVectors.remove(ASymbolsPreprocessor.KEY_TEXT);

		List<String> grammars = new ArrayList<>();
		newVectors.put("grammar", grammars);
		List<List<String>> textIndexToWords = new ArrayList<>();
		newVectors.put("words", textIndexToWords);

		texts.forEach(oneText -> {
			if (oneText == null) {
				grammars.add(null);
				textIndexToWords.add(null);
				return;
			}

			Map.Entry<String, List<String>> grammarAndWords = compressString(oneText);

			grammars.add(grammarAndWords.getKey());
			textIndexToWords.add(grammarAndWords.getValue());
		});

		return map;
	}

	private Map.Entry<String, List<String>> compressString(String oneText) {
		List<String> words = new ArrayList<>();

		String grammar = Pattern.compile("[\\w ]+").matcher(oneText).replaceAll(mr -> {
			String word = mr.group();

			words.add(word);

			return "w";
		});

		Map.Entry<String, List<String>> grammarAndWords = Map.entry(grammar, words);
		return grammarAndWords;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		if (output instanceof Map.Entry) {
			return decompressString((Entry<String, List<String>>) output);
		} else if (!(output instanceof Map)) {
			return output;
		}
		Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) output);

		Map<String, ?> vectors = (Map<String, ?>) map.remove(ColumnRepresentation.KEY_KEYTOVECTOR);

		Map<String, Object> newVectors = new LinkedHashMap<>(vectors);
		map.put(ColumnRepresentation.KEY_KEYTOVECTOR, newVectors);

		List<String> grammars = (List<String>) newVectors.remove("grammar");
		List<List<String>> textIndexToWords = (List<List<String>>) newVectors.remove("words");

		List<String> texts = new ArrayList<>();
		newVectors.put(ASymbolsPreprocessor.KEY_TEXT, texts);

		Iterator<List<String>> wordsIterator = textIndexToWords.iterator();

		grammars.forEach(grammar -> {
			// Make sure we iterate even if there is a null
			List<String> words = wordsIterator.next();

			if (grammar == null) {
				texts.add(null);
				return;
			}
			texts.add(decompressString(Map.entry(grammar, words)));
		});

		return map;
	}

	private String decompressString(Map.Entry<String, List<String>> grammarAndWords) {
		Iterator<String> words = grammarAndWords.getValue().iterator();
		String text = Pattern.compile("w").matcher(grammarAndWords.getKey()).replaceAll(mr -> {
			return words.next();
		});
		return text;
	}

}
