package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.AutocompleteStemmingPreprocessor;
import eu.solven.hutter_prize.reversible.AutocompleteWholeWordPreprocessor;
import eu.solven.hutter_prize.reversible.SkipClosingBrackets;
import eu.solven.hutter_prize.reversible.utilities.CompositeReversibleCompressor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestSkipClosingBrackets {
	final IReversibleCompressor preProcessor = new SkipClosingBrackets();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		String compressedPage = compressed.get("body").toString();

		Assertions.assertThat(compressedPage).hasSize(5458);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testAnarchism() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Anarchism");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		String compressedPage = compressed.get("body").toString();

		Assertions.assertThat(compressedPage).hasSize(12259);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

		}
	}

	@Test
	public void testBasic() throws IOException {
		String page = "[[Je]] m'[[appele]] [[Benoit]]";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[2Je m'[[6appele [[6Benoit");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testWithLeadingDigits() throws IOException {
		String page =
				"[[a]] m'[[1b]] [[BenoitBenoitBenoit]] [[c]] m'[[2d]] [[BenoitBenoitBenoit]] [[e]] m'[[2appele]] [[3f]]";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed)
				.isEqualTo(
						"[[1a m'[[_1b]] [[18BenoitBenoitBenoit [[1c m'[[_2d]] [[18BenoitBenoitBenoit [[1e m'[[_2appele]] [[_3f]]");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	// This is typically an error, with a missing `]`
	@Test
	public void testMissingCLosing_WithLeadingDigits() throws IOException {
		String page = "[[3 BC]-[[500 BC]]";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[_3 BC]-[[_500 BC]]");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	// This is typically an error, with a missing `]`
	@Test
	public void testMissingCLosing_WithoutLeadingDigits() throws IOException {
		String page = "[[BC]-[[500 BC]]";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[_BC]-[[_500 BC]]");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testMissingCLosing_NoclosingAtAll() throws IOException {
		String page = "[[BC-500 BC";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[_BC-500 BC");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_notAscii() throws IOException {
		String page = "t> [[M.I.N.D. Institute]] in California ([[17 October]] [[2002]])";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed)
				.isEqualTo("t> [[18M.I.N.D. Institute in California ([[_17 October]] [[_2002]])");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_escapeReplacement() throws IOException {
		String page = "[[Yo$1Lui]]";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[7Yo$1Lui");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_sAfterCLosing() throws IOException {
		String page = "Some [[page]]s";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Some [[4pages");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_beforeStemmingAutocomplete() throws IOException {
		IReversibleCompressor autocomplete = new AutocompleteStemmingPreprocessor();

		IReversibleCompressor composite = new CompositeReversibleCompressor(Arrays.asList(preProcessor, autocomplete));

		String page = "page [[page]]s";

		String compressed = (String) composite.compress(page);

		Assertions.assertThat(compressed).isEqualTo("page [[4>0es");

		{
			String decompressed = (String) composite.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_afterStemmingAutocomplete() throws IOException {
		IReversibleCompressor autocomplete = new AutocompleteStemmingPreprocessor();

		IReversibleCompressor composite = new CompositeReversibleCompressor(Arrays.asList(autocomplete, preProcessor));

		String page = "page [[page]]s";

		String compressed = (String) composite.compress(page);

		Assertions.assertThat(compressed).isEqualTo("page [[3>0es");

		{
			String decompressed = (String) composite.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_beforeWordAutocomplete() throws IOException {
		IReversibleCompressor autocomplete = new AutocompleteWholeWordPreprocessor();

		CompositeReversibleCompressor composite =
				new CompositeReversibleCompressor(Arrays.asList(preProcessor, autocomplete));

		String page = "Firefox [[Firefox]]";

		String compressed = (String) composite.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Firefox [[7F>");

		{
			String decompressed = (String) composite.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_afterWordAutocomplete() throws IOException {
		IReversibleCompressor autocomplete = new AutocompleteWholeWordPreprocessor();

		IReversibleCompressor composite = new CompositeReversibleCompressor(Arrays.asList(autocomplete, preProcessor));

		String page = "Firefox [[Firefox]]";

		String compressed = (String) composite.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Firefox [[2F>");

		{
			String decompressed = (String) composite.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testMoreThan100_doesNotStartWithDigit() throws IOException {
		String moreThan100 = IntStream.range(0, 100).mapToObj(i -> "someWord_" + i).collect(Collectors.joining());

		String page = "[[" + moreThan100 + "]]";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[_" + moreThan100 + "]]");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

}
