package eu.solven.hutter_prize.reversible;

/**
 * We extract tables as they hold a lot of symbols. However, they also hold text which should be compressed with the
 * original page.
 * 
 * @author Benoit Lacelle
 *
 */
public class TableHtmlPreprocessor extends PatternExtractorPreprocessor {

	public TableHtmlPreprocessor() {
		super("\\{thtml\\d+_", "<table", "\n</table>", "tables_html");
	}

}
