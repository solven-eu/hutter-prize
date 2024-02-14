package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.TableMarkdownPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestTablePreprocessor {
	final TableMarkdownPreprocessor preProcessor = new TableMarkdownPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/a");
		Assertions.assertThat(page).doesNotContain("table0_").contains("{| align=\"center\" cellspac");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body", "tables");

		Assertions.assertThat(compressed.get("body").toString())
				.contains("table0_")
				.doesNotContain("{| align=\"center\" cellspac");

		Assertions.assertThat(compressed.get("tables").toString()).contains("align=\"center\" cellspac");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}
}
