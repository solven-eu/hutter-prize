package eu.solven.hutter_prize.reversible.extract_language;

/**
 * We extract URL as they are generally not nice english grammar, and kind of follow its own grammar (e.g. no space)).
 * 
 * @author Benoit Lacelle
 *
 */
public class InfoboxAliasPreprocessor extends PatternExtractorPreprocessor {

	public InfoboxAliasPreprocessor() {
		super("IB\\d+_", "{{InfoBox", "}}", "infoboxes");
	}

}
