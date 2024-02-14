package eu.solven.hutter_prize.reversible;

/**
 * PHD9 was Hutter Prize winner in 2018, and some of its logic is still used by latest top-entries. We re-implement here
 * related logics.
 * 
 * @author Benoit Lacelle
 *
 */
// https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h
public class MathPreprocessor extends PatternExtractorPreprocessor {

	// Mathematical formulas holds a lot of small words, which would prevent later abbreviation (e.g. replacing
	// `because` by `bcz` is not possible if `bcz` word is consumed by a mathematical formulae
	public MathPreprocessor() {
		super("math_\\d+_", "&lt;math&gt;", "&lt;/math&gt;", "formulas");
	}

}
