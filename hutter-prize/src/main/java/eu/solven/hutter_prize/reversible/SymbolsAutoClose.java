package eu.solven.hutter_prize.reversible;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns `[[SomeWord]].` into `[[SomeWord.`
 * 
 * @author Benoit Lacelle
 */
public class SymbolsAutoClose extends AStringColumnEditorPreprocessor {
	private static final String ESCAPE = "!";

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		// Pattern leadingDigits = Pattern.compile("(\\d+).*");

		return Pattern.compile("(\\[\\[)([\\w |!]+)(\\]\\])?(?=[^\\w |!\\]]|$)").matcher(string).replaceAll(mr -> {
			String prefix = mr.group(1);
			String enclosed = mr.group(2);
			String closing = mr.group(3);

			if (null == closing) {
				// Not closed
				return Matcher.quoteReplacement(prefix + enclosed + "!");
			}

			// if (enclosed.startsWith(ESCAPE)) {
			// throw new IllegalStateException("Change the escaping policy");
			// }

			return Matcher.quoteReplacement(prefix + enclosed);
		});

	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		return Pattern.compile("(\\[\\[)([\\w |!]+)(?=[^\\w |!\\]]|$)").matcher(string).replaceAll(mr -> {
			String prefix = mr.group(1);
			String enclosed = mr.group(2);
			// String closing = mr.group(3);

			if (enclosed.endsWith("!")) {
				// Not closed
				return Matcher.quoteReplacement(prefix + enclosed.substring(0, enclosed.length() - ESCAPE.length()));
			}

			// if (enclosed.startsWith(ESCAPE)) {
			// throw new IllegalStateException("Change the escaping policy");
			// }

			return Matcher.quoteReplacement(prefix + enclosed + closing(prefix));
		});
	}

	private String closing(String prefix) {
		if ("[[".equals(prefix)) {
			return "]]";
		}
		// e.g. `===AAA===`
		return prefix;
	}

}
