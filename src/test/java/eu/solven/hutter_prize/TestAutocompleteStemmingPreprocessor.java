package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.AutocompleteStemmingPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestAutocompleteStemmingPreprocessor {
	final AutocompleteStemmingPreprocessor preProcessor = new AutocompleteStemmingPreprocessor();

	@Test
	public void testAnarchism() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/anarchism");
		Assertions.assertThat(page).doesNotContain(">>").contains("'''Anarchism'''");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body");

		// Assertions.assertThat(compressed.get("body").toString()).contains("'''g>'''").doesNotContain("'''Google'''");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void test_similar() throws IOException {
		String page = "Anarchy oui anarchism non anarchist benoit anarchists";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Anarchy oui anarchism non >0ist benoit >0ists");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_wordDoesNotStartByStem() throws IOException {
		String page = "originated used Revolution. used...";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("originated used Revolution. >0ed...");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_bug() throws IOException {
		String page = "[[sans-culotte|''sans-culottes'']]";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[sans-culotte|''>1s->1tes'']]");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_sameWord() throws IOException {
		String page = "{{Anarchism}}\n	'''Anarchism''' \n";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("{{Anarchism}}\n	'''>0ism''' \n");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

}
