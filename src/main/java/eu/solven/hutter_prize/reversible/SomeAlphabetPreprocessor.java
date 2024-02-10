package eu.solven.hutter_prize.reversible;

/**
 * Extract Korean, in order to pack together most uses of Korean alphabet
 * 
 * @author Benoit Lacelle
 *
 */
public class SomeAlphabetPreprocessor extends PatternExtractorPreprocessor {

	public SomeAlphabetPreprocessor(String languageCode) {
		super(languageCode + "\\d+_", "\n\\[\\[" + languageCode + ":", "\\]\\]", languageCode);
	}

}
