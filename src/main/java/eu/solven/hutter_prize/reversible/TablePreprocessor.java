package eu.solven.hutter_prize.reversible;

/**
 * We extract tables as they hold a lot of symbols. However, they also hold text which should be compressed with the
 * original page.
 * 
 * @author Benoit Lacelle
 *
 */
public class TablePreprocessor extends PatternExtractorPreprocessor {

	public TablePreprocessor() {
		super("\\{table\\d+_", "\\{\\| ", "\n\\|\\}", "tables");
	}

}
