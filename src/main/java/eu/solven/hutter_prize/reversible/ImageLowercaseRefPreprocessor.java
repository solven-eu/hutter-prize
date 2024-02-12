package eu.solven.hutter_prize.reversible;

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
				"ImageLC_\\d+_",
				"\\[\\[image:",
				"\\]\\]",
				"imageLCRefs");
	}

}
