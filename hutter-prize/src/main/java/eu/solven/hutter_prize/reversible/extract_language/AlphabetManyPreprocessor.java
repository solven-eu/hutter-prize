package eu.solven.hutter_prize.reversible.extract_language;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extract blocks known as holding many non-ASCII characters, in order to pack together most uses of each alphabet.
 * 
 * @author Benoit Lacelle
 *
 */
public class AlphabetManyPreprocessor extends PatternExtractorPreprocessor {

	public AlphabetManyPreprocessor(String... languageCodes) {
		super("zz" + "\\d+_",
				"\n\\[\\[" + concatForRegex(languageCodes) + ":",
				"\\]\\]",
				Stream.of(languageCodes).collect(Collectors.joining("_")));
	}

	private static String concatForRegex(String... languageCodes) {
		return Stream.of(languageCodes).collect(Collectors.joining("|", "(", ")"));
	}

	@Override
	protected String regexToQuoted(String regex) {
		if (regex.startsWith("\n\\[\\[(")) {
			return "\n[[";
		} else {
			return super.regexToQuoted(regex);
		}
	}

}
