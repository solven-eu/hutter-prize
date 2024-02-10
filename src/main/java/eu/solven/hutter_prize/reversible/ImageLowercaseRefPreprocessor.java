package eu.solven.hutter_prize.reversible;

/**
 * We extract `[[image:]]`
 * 
 * @author Benoit Lacelle
 *
 */
public class ImageLowercaseRefPreprocessor extends PatternExtractorPreprocessor {
	public ImageLowercaseRefPreprocessor() {
		super("imagelc_\\d+_", "\\[\\[image:", "\\]\\]", "imageLCRefs");
	}

}
