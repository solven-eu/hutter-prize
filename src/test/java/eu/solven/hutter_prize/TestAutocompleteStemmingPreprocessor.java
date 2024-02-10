package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.util.StringUtils;

import eu.solven.hutter_prize.reversible.AutocompleteStemmingPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestAutocompleteStemmingPreprocessor {
	final AutocompleteStemmingPreprocessor preProcessor = new AutocompleteStemmingPreprocessor(9999);

	@Test
	public void testAnarchism() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/anarchism");
		Assertions.assertThat(page).doesNotContain(">>").contains("'''Anarchism'''");

		Map<String, ?> compressedObject = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressedObject).containsKeys("body");

		String compressedPage = compressedObject.get("body").toString();

		Assertions.assertThat(compressedPage).hasSize(11670);
		Assertions.assertThat(StringUtils.countOccurrencesOf(compressedPage, "anarchist")).isEqualTo(1);

		// Assertions.assertThat(compressed.get("body").toString()).contains("'''g>'''").doesNotContain("'''Google'''");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressedObject);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testEmpty() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/empty");

		Map<String, ?> compressedObject = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressedObject).containsKeys("body");

		String compressedPage = compressedObject.get("body").toString();

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressedObject);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}

		// Ensure we commit this as being an empty file
		// It is used to easily create new unitTests, by copy-pasting large texts
		Assertions.assertThat(compressedPage).isEmpty();
		Assertions.assertThat(page).isEmpty();
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
		String page = "originated anarchy Revolution. anarched...";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("originated anarchy Revolution. >0ed...");

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

	@Test
	public void test_escape() throws IOException {
		String page = "123<ref>456";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("123<ref>>456");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_escape2() throws IOException {
		String page = "123<<ref>>456";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("123<<ref>>>>456");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_escape3() throws IOException {
		String page = "Henry, <<br>>Henrique";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Henry, <<br>>>>Henrique");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_SmallStem() throws IOException {
		String page = "uses used";
		String compressed = (String) preProcessor.compress(page);

		// It is pointless to replace `used` by `>0ed`
		Assertions.assertThat(compressed).isEqualTo("uses used");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_escapeBug() throws IOException {
		String page = "Station >>Polar Stater sealer";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Station >>>>Polar >0er sealer");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

}
