package eu.solven.hutter_prize.reversible;

/**
 * Extract blocks known as holding many non-ASCII characters, in order to pack together most uses of each alphabet.
 * 
 * @author Benoit Lacelle
 *
 */
public class AlphabetSomePreprocessor extends PatternExtractorPreprocessor {

	public AlphabetSomePreprocessor(String languageCode) {
		super(languageCode + "\\d+_", "\n\\[\\[" + languageCode + ":", "\\]\\]", languageCode);
	}

}
