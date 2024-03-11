package eu.solven.hutter_prize.reversible.extract_language;

/**
 * We extract tables as they hold a lot of symbols. However, they also hold text which should be compressed with the
 * original page.
 * 
 * @author Benoit Lacelle
 *
 */
public class TableMarkdownAliasPreprocessor extends PatternExtractorPreprocessor {

	public TableMarkdownAliasPreprocessor() {
		super("TMD\\d+_", "\\{\\|", "\n\\|\\}", "tables_markdown");
	}

}
