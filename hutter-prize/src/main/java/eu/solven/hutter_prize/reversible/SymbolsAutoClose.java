package eu.solven.hutter_prize.reversible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

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

	private static Map<String, String> openingToIn = ImmutableMap.<String, String>builder().put("[[", ESCAPE).build();

	final String compressRegex = "(\\[\\[)([" + IN + "]+(?<! ))(\\]\\])?(?= ?[^" + IN + "\\]]|$)";
	final String decompressRegex = "(\\[\\[)([" + IN + "]+(?<! ))(?= ?[^" + IN + "\\]]|$)";

	final List<Pattern> openingPatterns = new ArrayList<>();
	final List<Pattern> closingPatterns = new ArrayList<>();

	public SymbolsAutoClose() {
		openingToIn.forEach((opening, in) -> {
			String escapedOpening = escaped(opening);
			String closing = closing(opening);
			String escapedClosing = escaped(closing);

			String regexOpening = "(" + escapedOpening + ")([" + IN + "]+(?<! ))";
			String regexClosing = "(?= ?[^" + IN + escapedClosing + "]|$)";

			final String compressRegex = regexOpening + "(" + escapedClosing + ")?" + regexClosing;
			final String decompressRegex = regexOpening + regexClosing;

			LOGGER.info("compressRegex: {}", compressRegex);
			openingPatterns.add(Pattern.compile(compressRegex));
			LOGGER.info("decompressRegex: {}", decompressRegex);
			closingPatterns.add(Pattern.compile(decompressRegex));
		});

		// We want to decompress in reverse order than we compressed
		Collections.reverse(closingPatterns);
	}

	private String escaped(String opening) {
		int[] escapedOpeningArray = opening.chars().flatMap(c -> IntStream.of('\\', c)).toArray();
		String escapedOpening = new String(escapedOpeningArray, 0, escapedOpeningArray.length);
		return escapedOpening;
	}

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		String mutated = string;

		for (Pattern opening : openingPatterns) {
			mutated = opening.matcher(mutated).replaceAll(mr -> {
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

		return mutated;

	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		String mutated = string;

		for (Pattern closing : closingPatterns) {
			mutated = closing.matcher(mutated).replaceAll(mr -> {
				String prefix = mr.group(1);
				String enclosed = mr.group(2);
				// String closing = mr.group(3);

				if (enclosed.endsWith("!")) {
					// Not closed
					return Matcher
							.quoteReplacement(prefix + enclosed.substring(0, enclosed.length() - ESCAPE.length()));
				}

				// if (enclosed.startsWith(ESCAPE)) {
				// throw new IllegalStateException("Change the escaping policy");
				// }

				return Matcher.quoteReplacement(prefix + enclosed + closing(prefix));
			});
		}

		return mutated;
	}

	private String closing(String prefix) {
		if (prefix.length() > 1) {
			return prefix.codePoints()
					.mapToObj(codePoint -> Character.toString(codePoint))
					.map(s -> closing(s))
					.collect(Collectors.joining());
		}

		if ("[".equals(prefix)) {
			return "]";
		} else if ("{".equals(prefix)) {
			return "}";
		} else if ("(".equals(prefix)) {
			return ")";
		}
		// e.g. `===AAA===`
		return prefix;
	}

}
