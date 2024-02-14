package eu.solven.hutter_prize.reversible;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * We encode not standard characters into the following syntax `&$nnnn;` where `nnnn` is the decimal representation of
 * the code. It refers to the UTF8 standard `&#nnnn;` notation
 * (https://en.wikipedia.org/wiki/List_of_Unicode_characters).
 * 
 * @author Benoit Lacelle
 */
public class CharacterEncodingPreprocessor extends ASymbolsPreprocessor {
	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		if (string.contains("&$")) {
			throw new IllegalArgumentException("TODO Escape `&$`");
		}

		return string.codePoints().mapToObj(codePoint -> {
			if (codePoint >= 0x007F) {
				return "&$" + codePoint + ";";
			} else {
				return Character.toString(codePoint);
			}
		}).collect(Collectors.joining());
	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		return Pattern.compile("&\\$(\\d+);")
				.matcher(string)
				.replaceAll(mr -> Character.toString(Integer.parseInt(mr.group(1))));
	}
}