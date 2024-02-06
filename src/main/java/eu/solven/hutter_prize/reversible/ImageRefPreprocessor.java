package eu.solven.hutter_prize.reversible;

/**
 * We extract `[[Image:]]`
 * 
 * @author Benoit Lacelle
 *
 */
public class ImageRefPreprocessor extends PatternExtractorPreprocessor {
	public ImageRefPreprocessor() {
		super("image_\\d+_", "\\[\\[Image:", "\\]\\]", "imageRefs");
	}

}
