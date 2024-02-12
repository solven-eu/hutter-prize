package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.util.StringUtils;

import eu.solven.hutter_prize.reversible.SentenceStartsWithUCPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestSentenceStartsWithUCPreprocessor {
	final SentenceStartsWithUCPreprocessor preProcessor = new SentenceStartsWithUCPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		String compressedPage = compressed.get("body").toString();

		Assertions.assertThat(compressedPage).hasSize(5482);
		Assertions.assertThat(StringUtils.countOccurrencesOf(compressedPage, "\nA")).isEqualTo(4);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testAsperger() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Asperger");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		String compressedPage = compressed.get("body").toString();

		Assertions.assertThat(compressedPage).hasSize(48740);
		Assertions.assertThat(StringUtils.countOccurrencesOf(compressedPage, "FLC")).isEqualTo(1);

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

		String compressed = (String) preProcessor.compress(page);
		Assertions.assertThat(compressed).isEqualTo("imageFLC");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_UpperCaseEndsLC() throws IOException {
		String page = "ImageFLC";

		Assertions.assertThatThrownBy(() -> preProcessor.compress(page)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testBasic_Initials() throws IOException {
		String page = "Je m appele J.M. Youpi";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("je m appele J.M. youpi");
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
	public void testBasic_oneLetter() throws IOException {
		String page = "I am Benoit";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("i am Benoit");
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
	public void testBasic_afterEol() throws IOException {
		String page = "Gogol.\n\nA googol is the ...";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("gogol.\n\na googol is the ...");
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
	public void testBasic_afterSpecial() throws IOException {
		String page = "Je suis (Benoit Lacelle)";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("je suis (Benoit Lacelle)");
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

	// `U.S. state`
	@Test
	public void testBasic_particule() throws IOException {
		String page = "Jenny P. d'Hericourt";
		// Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("jenny P. dFLC'Hericourt");
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
