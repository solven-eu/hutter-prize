package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.AutocompleteStemmingPreprocessor;
import eu.solven.hutter_prize.reversible.AutocompleteWholeWordPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestAutocompleteWholeWordPreprocessor {
	final AutocompleteWholeWordPreprocessor preProcessor = new AutocompleteWholeWordPreprocessor();
	final AutocompleteWholeWordPreprocessor preProcessorExtreme = new AutocompleteWholeWordPreprocessor(1);

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Googol");
		Assertions.assertThat(page).doesNotContain(">>").contains("'''googol'''").hasSize(5482);

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body");

		String compressedPage = compressed.get("body").toString();
		Assertions.assertThat(compressedPage).contains("'''g>'''").doesNotContain("'''Google'''").hasSize(4952);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testAntarctica() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Antarctica");
		Assertions.assertThat(page).hasSize(28035);

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body");

		String compressedPage = compressed.get("body").toString();
		Assertions.assertThat(compressedPage)
				// .contains("'''g>'''").doesNotContain("'''Google'''")
				.hasSize(23483);

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

		Assertions.assertThat(compressed).isEqualTo("the that large the");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_accent() throws IOException {
		String page = "prénom prénom";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("prénom p>");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_BugWithRegexWordsAtTheBeginning() throws IOException {
		String page = "Google google.com Googol Google";
		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Google google.com Googol Google");

		{
			String decompressed = preProcessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_Escape() throws IOException {
		String page = "the compa> comp";
		String compressed = (String) preProcessorExtreme.compress(page);

		Assertions.assertThat(compressed).isEqualTo("the compa>> comp");

		{
			String decompressed = preProcessorExtreme.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_Escape_ok() throws IOException {
		// We test various memory-sizes, to detect if `compa>` is registered or not
		for (int i = 1; i < 16; i++) {
			AutocompleteWholeWordPreprocessor preprocessor = new AutocompleteWholeWordPreprocessor(i);

			String page = "the compa> compa the";
			String compressed = (String) preprocessor.compress(page);

			if (i >= 2) {
				// `compa>` should not be registered amongst allowed words
				Assertions.assertThat(compressed).isEqualTo("the compa>> compa t>");
			} else {
				Assertions.assertThat(compressed).isEqualTo("the compa>> compa the");
			}

			{
				String decompressed = preprocessor.decompress(compressed).toString();
				Assertions.assertThat(decompressed).isEqualTo(page);
			}
		}
	}

	@Test
	public void test_Escape_Left() throws IOException {
		String page = "the <compa ompa";
		String compressed = (String) preProcessorExtreme.compress(page);

		Assertions.assertThat(compressed).isEqualTo("the <<compa ompa");

		{
			String decompressed = preProcessorExtreme.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_Escape_Both() throws IOException {
		String page = "ab<ref>cd";

		IReversibleCompressor autocomplete =
				new CompositeReversibleCompressor(Arrays.asList(preProcessor, new AutocompleteStemmingPreprocessor()));

		String compressed = (String) autocomplete.compress(page);

		Assertions.assertThat(compressed).isEqualTo("ab<<ref>>>cd");

		{
			String decompressed = autocomplete.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_Escape_Both_naked() throws IOException {
		String page = "<ref>";

		IReversibleCompressor autocomplete =
				new CompositeReversibleCompressor(Arrays.asList(preProcessor, new AutocompleteStemmingPreprocessor()));

		String compressed = (String) autocomplete.compress(page);

		Assertions.assertThat(compressed).isEqualTo("<<ref>>");

		{
			String decompressed = autocomplete.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void test_Escape_Both_noword() throws IOException {
		String page = "ab<>cd";

		IReversibleCompressor autocomplete =
				new CompositeReversibleCompressor(Arrays.asList(preProcessor, new AutocompleteStemmingPreprocessor()));

		String compressed = (String) autocomplete.compress(page);

		Assertions.assertThat(compressed).isEqualTo("ab<>>cd");

		{
			String decompressed = autocomplete.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testEmpty() throws IOException {
		IReversibleCompressor autocomplete =
				new CompositeReversibleCompressor(Arrays.asList(preProcessor, new AutocompleteStemmingPreprocessor()));

		String page = PepperResourceHelper.loadAsString("/pages/empty");

		Map<String, ?> compressedObject = (Map<String, ?>) autocomplete.compress(Map.of("body", page));

		Assertions.assertThat(compressedObject).containsKeys("body");

		String compressedPage = compressedObject.get("body").toString();

		{
			Map<String, ?> decompressed = (Map<String, ?>) autocomplete.decompress(compressedObject);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}

		// Ensure we commit this as being an empty file
		// It is used to easily create new unitTests, by copy-pasting large texts
		Assertions.assertThat(compressedPage).isEmpty();
		Assertions.assertThat(page).isEmpty();
	}
}
