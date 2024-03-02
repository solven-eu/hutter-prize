package eu.solven.hutter_prize.reversible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

	private static final String IN = "\\p{IsLatin})0-9_ |()\\-:?" + ESCAPE;

	// We should start by inner then outer
	// For instance, as we would handle `{{a [[Banana]] is a [[fruit]]}}`, we should handle `[[` then `{{`
	private static Map<String, String> openingToIn = ImmutableMap.<String, String>builder()
			// .put("''[[", IN)

			// `'` is more often around `[[` than inside
			.put("[[", IN)
			.put("{{", IN + "\\[\\]")
			.put("==", IN + "'")
			.put("''", IN)
			.build();

	// final String compressRegex = "(\\[\\[)([" + IN + "]+(?<! ))(\\]\\])?(?= ?[^" + IN + "\\]]|$)";
	// final String decompressRegex = "(\\[\\[)([" + IN + "]+(?<! ))(?= ?[^" + IN + "\\]]|$)";

	// final List<Pattern> openingPatterns = new ArrayList<>();
	// final List<Pattern> closingPatterns = new ArrayList<>();

	final Map<String, Pattern> openingToPattern = new LinkedHashMap<>();

	public SymbolsAutoClose() {
		openingToIn.forEach((opening, in) -> {
			Matcher sanityMatcher = Pattern.compile("[" + in + "]").matcher(opening);
			if (sanityMatcher.find()) {
				String faulty = sanityMatcher.group();
				throw new IllegalStateException(in + " and " + opening + " have commons chars: " + faulty);
			}

			String escapedOpening = escaped(opening);
			String closing = closing(opening);
			String escapedClosing = escaped(closing);

			// The opening may end with a space
			String regexOpening = "(" + escapedOpening + " ?)";
			// the content should neither start nor end with a space
			String regexContent = "(" + "(?!= )" + "[" + in + "]+" + "(?<! )" + ")";
			// But the closing may start with a space
			String regexOptClosing = "( ?" + escapedClosing + ")?";
			String regexTrail = "(?= ?(.|$))";

			final String compressRegex = regexOpening + regexContent + regexOptClosing + regexTrail;
			// final String decompressRegex = regexOpening + regexClosing;

			LOGGER.info("compressRegex: {}", compressRegex);
			openingToPattern.put(opening, Pattern.compile(compressRegex, Pattern.MULTILINE));
			// openingPatterns.add(Pattern.compile(compressRegex, Pattern.MULTILINE));
		});

		// We want to decompress in reverse order than we compressed
		// closingPatterns.addAll(openingPatterns);
		// Collections.reverse(closingPatterns);
	}

	private String escaped(String opening) {
		int[] escapedOpeningArray = opening.chars().flatMap(c -> IntStream.of('\\', c)).toArray();
		String escapedOpening = new String(escapedOpeningArray, 0, escapedOpeningArray.length);
		return escapedOpening;
	}

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		String mutated = string;

		for (Entry<String, Pattern> openingPattern : openingToPattern.entrySet()) {
			mutated = openingPattern.getValue().matcher(mutated).replaceAll(mr -> {
				String opening = mr.group(1);
				String enclosed = mr.group(2);
				String closing = mr.group(3);
				String suffix = mr.group(4);

				String in = openingToIn.get(openingPattern.getKey());

				if (Pattern.compile("[" + in + "]").matcher(suffix).find()) {
					// The closed pattern is followed by a character which is not a stronger symbol:
					// If we remove the closing, we can not guess this is the place to restore the closing
					// So we have to keep the closing.
					return Matcher.quoteReplacement(opening + enclosed + closing);
				}

				if (null == closing) {
					// Not closed
					return Matcher.quoteReplacement(opening + enclosed + "!");
				}

				if (!suffix.isEmpty() && closing.contains(suffix)) {
					// The closing is doubled
					return Matcher.quoteReplacement(opening + enclosed + closing);
				}

				if (opening.endsWith(" ") ^ closing.startsWith(" ")) {
					// We have asymmetrical spaces
					return Matcher.quoteReplacement(opening + enclosed + closing);
				}

				// if (enclosed.startsWith(ESCAPE)) {
				// throw new IllegalStateException("Change the escaping policy");
				// }

				return Matcher.quoteReplacement(opening + enclosed);
			});
		}

		return mutated;

	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		String mutated = string;

		List<String> closings = new ArrayList<>(openingToPattern.keySet());
		// We want to decompress in reverse order than we compressed
		Collections.reverse(closings);

		for (String closingP : closings) {
			mutated = openingToPattern.get(closingP).matcher(mutated).replaceAll(mr -> {
				String prefix = mr.group(1);
				String enclosed = mr.group(2);
				String closing = mr.group(3);
				// String suffix = mr.group(3);

				// if (Pattern.compile(IN).matcher(suffix).find()) {
				// // This is a closing which has not been removed
				// return Matcher.quoteReplacement(prefix + enclosed + suffix);
				// }

				if (null != closing) {
					// Closing has not been removed
					return Matcher.quoteReplacement(prefix + enclosed + closing);
				}

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
			int[] codePoints = prefix.codePoints().toArray();

			IntStream reverseOrderedCodepoints =
					IntStream.range(0, codePoints.length).map(i -> codePoints.length - i - 1).map(i -> codePoints[i]);
			return reverseOrderedCodepoints.mapToObj(codePoint -> Character.toString(codePoint))
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
