package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.extract_language.MathPreprocessor;
import eu.solven.pepper.collection.PepperMapHelper;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestMathPreprocessor {
	final IReversibleCompressor preprocessor = new MathPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		Assertions.assertThat(page).hasSize(5482);

		Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		Map<String, ?> compressed = (Map<String, ?>) preprocessor.compress(Map.of("body", page));
		Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "body")).hasSize(5287);

		Assertions.assertThat(compressed).containsKeys("body", "formulas");

		Assertions.assertThat(compressed.get("body").toString())
				.contains("math_0_")
				.doesNotContain("10^{8 \\times 10^{16}}");

		Assertions.assertThat(compressed.get("formulas").toString()).contains("10^{8 \\times 10^{16}}");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preprocessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}
}
