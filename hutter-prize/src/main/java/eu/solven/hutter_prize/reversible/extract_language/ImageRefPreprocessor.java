package eu.solven.hutter_prize.reversible.extract_language;

/**
 * We extract `[[Image:]]`
 * 
 * @author Benoit Lacelle
 *
 */
public class ImageRefPreprocessor extends PatternExtractorPreprocessor {
	public ImageRefPreprocessor() {
		super(
				// UpperCase to prevent interactions with SentenceStartsWithUCPreprocessor
				"Img\\d+_",
				"\\[\\[Image:",
				"\\]\\]",
				"imageRefs");
	}

}