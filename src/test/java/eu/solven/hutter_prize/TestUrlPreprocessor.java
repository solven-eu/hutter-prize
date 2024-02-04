package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.UrlPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestUrlPreprocessor {
	final UrlPreprocessor preProcessor = new UrlPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		Assertions.assertThat(page).doesNotContain("math0").contains("http://www.googol.com/");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body", "urls");

		Assertions.assertThat(compressed.get("body").toString())
				.contains("url5")
				.doesNotContain("http://www.googol.com/");

		Assertions.assertThat(compressed.get("urls").toString()).contains("http://www.googol.com/");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}
}
