package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.AutocompletePreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestAutocompletePreprocessor {
	final AutocompletePreprocessor preProcessor = new AutocompletePreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		Assertions.assertThat(page).doesNotContain(">>").contains("'''googol'''");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body");

		Assertions.assertThat(compressed.get("body").toString()).contains("'''g>'''").doesNotContain("'''Google'''");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void test_thethe() throws IOException {
		String page = "the the";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("the t>");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_thethatthe() throws IOException {
		String page = "the that the";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("the that <e");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_thethatlargethe() throws IOException {
		String page = "the that large the";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("the that large <he");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_BugWithRegexWordsAtTheBeginning() throws IOException {
		String page = "Google google.com Googol Google";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Google google.com Googol Googl>");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testAnarchism() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/anarchism");
		Assertions.assertThat(page).doesNotContain(">>").contains("'''Anarchism'''");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body");

		Assertions.assertThat(compressed.get("body").toString()).contains("'''g>'''").doesNotContain("'''Google'''");

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

		Assertions.assertThat(compressed).isEqualTo("Anarchy oui >hism non >hist benoit >hists");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}
}
