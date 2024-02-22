package eu.solven.hutter_prize.reversible.extract_language;

/**
 * We extract `[[image:]]`
 * 
 * @author Benoit Lacelle
 *
 */
public class ImageLowercaseRefPreprocessor extends PatternExtractorPreprocessor {
	public ImageLowercaseRefPreprocessor() {
		super(
				// UpperCase to prevent interactions with SentenceStartsWithUCPreprocessor
				"ILC\\d+_",
				"\\[\\[image:",
				"\\]\\]",
				"imageLCRefs");
	}

}
