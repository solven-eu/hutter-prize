package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.extract_language.UrlAliasPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestUrlPreprocessor {
	final UrlAliasPreprocessor preProcessor = new UrlAliasPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		Assertions.assertThat(page).doesNotContain("math0").contains("http://");

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body", "urls");

		Assertions.assertThat(compressed.get("body").toString()).contains("URL5_").doesNotContain("http://www");

		Assertions.assertThat(compressed.get("urls").toString()).contains("www.googol.com/");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}
}
