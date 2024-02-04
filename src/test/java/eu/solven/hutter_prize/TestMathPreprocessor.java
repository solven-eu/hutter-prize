package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.MathPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestMathPreprocessor {
	final MathPreprocessor mathPreprocessor = new MathPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		Assertions.assertThat(page).doesNotContain("math(0)").contains("10^{8 \\times 10^{16}}");

		Map<String, ?> compressed = (Map<String, ?>) mathPreprocessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body", "math");

		Assertions.assertThat(compressed.get("body").toString())
				.contains("math(0)")
				.doesNotContain("10^{8 \\times 10^{16}}");

		Assertions.assertThat(compressed.get("math").toString()).contains("10^{8 \\times 10^{16}}");

		{
			Map<String, ?> decompressed = (Map<String, ?>) mathPreprocessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}
}
