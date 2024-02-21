package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.extract_language.ImageRefPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestImageRefsPreprocessor {
	final ImageRefPreprocessor reprocessor = new ImageRefPreprocessor();

	@Test
	public void testAnarchism() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/anarchism");
		Assertions.assertThat(page).doesNotContain("Img0_").contains("WilliamGodwin.jpg");

		Map<String, ?> compressed = (Map<String, ?>) reprocessor.compress(Map.of("body", page));

		Assertions.assertThat(compressed).containsKeys("body", "imageRefs");

		Assertions.assertThat(compressed.get("body").toString()).contains("Img0_").doesNotContain("WilliamGodwin.jpg");

		Assertions.assertThat(compressed.get("imageRefs").toString()).contains("WilliamGodwin.jpg");

		{
			Map<String, ?> decompressed = (Map<String, ?>) reprocessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}
}
