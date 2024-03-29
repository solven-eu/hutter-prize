package eu.solven.hutter_prize.reversible.extract_language;

/**
 * We extract URL as they are generally not nice english grammar, and kind of follow its own grammar (e.g. no space)).
 * 
 * @author Benoit Lacelle
 *
 */
public class UrlAliasPreprocessor extends PatternExtractorPreprocessor {

	private static final String PATTERN_CLOSING = "[\\] ]";

	public UrlAliasPreprocessor() {
		super(
				// We handle a subset of potential scheme
				"URL\\d+_",
				// Either the URL is closed by `]`, or the link is couple with a nice sentence, separated of the URL by
				// a ` `
				"\\[http://",
				PATTERN_CLOSING,
				"urls");
	}

	@Override
	protected String regexToQuoted(String regex) {
		if (PATTERN_CLOSING.equals(regex)) {
			// We kept the ` ` or `]` in the URL
			return "";
		} else {
			return super.regexToQuoted(regex);
		}
	}

}
