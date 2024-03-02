package eu.solven.hutter_prize.reversible;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns `[[SomeWord]].` into `[[SomeWord.`
 * 
 * @author Benoit Lacelle
 */
public class SymbolsAutoClose extends AStringColumnEditorPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(SymbolsAutoClose.class);

	private static final String ESCAPE = "!";

	private static final String IN_STRICT = "\\w|():" + ESCAPE;
	// We allow a space, but not as last character
	// e.g. `[[A B]]` but not `[[A B ]]`
	private static final String IN = IN_STRICT + " ";

	final String compressRegex = "(\\[\\[)([" + IN + "]+(?<! ))(\\]\\])?(?= ?[^" + IN + "\\]]|$)";
	final String decompressRegex = "(\\[\\[)([" + IN + "]+(?<! ))(?= ?[^" + IN + "\\]]|$)";

	public SymbolsAutoClose() {
		LOGGER.info("compressRegex: {}", compressRegex);
		LOGGER.info("decompressRegex: {}", decompressRegex);
	}

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		// Pattern leadingDigits = Pattern.compile("(\\d+).*");

		return Pattern.compile(compressRegex).matcher(string).replaceAll(mr -> {
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
		return Pattern.compile(decompressRegex).matcher(string).replaceAll(mr -> {
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
