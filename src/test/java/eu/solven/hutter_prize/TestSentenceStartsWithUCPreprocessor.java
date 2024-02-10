package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.SentenceStartsWithUCPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestSentenceStartsWithUCPreprocessor {
	final SentenceStartsWithUCPreprocessor preProcessor = new SentenceStartsWithUCPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		// Assertions.assertThat(compressed).containsKeys("body", "formulas");
		//
		// Assertions.assertThat(compressed.get("body").toString())
		// .contains("math_0_")
		// .doesNotContain("10^{8 \\times 10^{16}}");
		//
		// Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testBasic() throws IOException {
		String page = "Je m'appele Benoit et je mange des babanes. Je m'appele Benoit et je mange des babanes.";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed)
				.isEqualTo("je m'appele Benoit et je mange des babanes. je m'appele Benoit et je mange des babanes.");
		//
		// Assertions.assertThat(compressed.get("body").toString())
		// .contains("math_0_")
		// .doesNotContain("10^{8 \\times 10^{16}}");
		//
		// Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testAlwaysUpperCase_alwaysFirstWord() throws IOException {
		String page = "Anarchism like arnarchists. Anarchism like arnarchists.";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("anarchism like arnarchists. anarchism like arnarchists.");
		//
		// Assertions.assertThat(compressed.get("body").toString())
		// .contains("math_0_")
		// .doesNotContain("10^{8 \\times 10^{16}}");
		//
		// Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testAlwaysUpperCase_onceAsProperNoun() throws IOException {
		String page = "Anarchism like arnarchists. Anarchists like Anarchism.";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Anarchism like arnarchists. anarchists like Anarchism.");
		//
		// Assertions.assertThat(compressed.get("body").toString())
		// .contains("math_0_")
		// .doesNotContain("10^{8 \\times 10^{16}}");
		//
		// Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_mixedCase() throws IOException {
		String page = "AnarChism like arnarchists. Anarchism like arnarchists.";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("anarChism like arnarchists. anarchism like arnarchists.");
		//
		// Assertions.assertThat(compressed.get("body").toString())
		// .contains("math_0_")
		// .doesNotContain("10^{8 \\times 10^{16}}");
		//
		// Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_UpperCase() throws IOException {
		String page = "They Like Me";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("they Like Me");
		//
		// Assertions.assertThat(compressed.get("body").toString())
		// .contains("math_0_")
		// .doesNotContain("10^{8 \\times 10^{16}}");
		//
		// Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_lowerCase() throws IOException {
		String page = "image";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("image");
		//
		// Assertions.assertThat(compressed.get("body").toString())
		// .contains("math_0_")
		// .doesNotContain("10^{8 \\times 10^{16}}");
		//
		// Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	// @Test
	// public void testEdgy() throws IOException {
	// String page = "&lt;/ref&gt; image_0_ Anarchists";
	// // Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");
	//
	// String compressed = (String) preProcessor.compress(page);
	//
	// Assertions.assertThat(compressed).isEqualTo("they Like Me");
	// //
	// // Assertions.assertThat(compressed.get("body").toString())
	// // .contains("math_0_")
	// // .doesNotContain("10^{8 \\times 10^{16}}");
	// //
	// // Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");
	//
	// {
	// String decompressed = (String) preProcessor.decompress(compressed);
	// Assertions.assertThat(decompressed).isEqualTo(page);
	// }
	// }
}
