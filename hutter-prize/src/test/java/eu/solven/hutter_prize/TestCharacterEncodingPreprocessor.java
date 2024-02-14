package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.CharacterEncodingPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestCharacterEncodingPreprocessor {
	final CharacterEncodingPreprocessor preProcessor = new CharacterEncodingPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		Assertions.assertThat(page).hasSize(5482);

		Map<String, ?> compressed = (Map<String, ?>) preProcessor.compress(Map.of("body", page));

		String compressedPage = compressed.get("body").toString();

		Assertions.assertThat(compressedPage).hasSize(5614);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testBasic() throws IOException {
		String page = "Je m'appele Benoit";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Je m'appele Benoit");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_notAscii() throws IOException {
		String page = "l'être-humain mâle et femelle - Lettre à P.J.";

		String compressed = (String) preProcessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("l'&$234;tre-humain m&$226;le et femelle - Lettre &$224; P.J.");

		{
			String decompressed = (String) preProcessor.decompress(compressed);
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}
}
