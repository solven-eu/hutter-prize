package eu.solven.hutter_prize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.google.common.base.Strings;

import eu.solven.hutter_prize.reversible.PackCharactersPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestPackCharactersPreprocessor {
	final PackCharactersPreprocessor preprocessor = new PackCharactersPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Googol");
		Assertions.assertThat(page).hasSize(5482);

		Map<String, ?> compressed = (Map<String, ?>) preprocessor.compress(Map.of("body", page));
		byte[] compressedBodyAsBytes = (byte[]) compressed.get("body");
		Assertions.assertThat(compressedBodyAsBytes).hasSize(4836);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preprocessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testBasic() throws IOException {
		String page = Strings.repeat("Benoit", 16);
		Assertions.assertThat(page.getBytes(StandardCharsets.UTF_8)).hasSize(96);

		byte[] compressed = (byte[]) preprocessor.compress(page);

		Assertions.assertThat(compressed).hasSize(88);

		{
			String decompressed = (String) preprocessor.decompress(compressed);

			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_HighSurrogate() throws IOException {
		String page = Strings.repeat("Benoĩt", 16);
		Assertions.assertThat(page.getBytes(StandardCharsets.UTF_8)).hasSize(112);

		byte[] compressed = (byte[]) preprocessor.compress(page);

		Assertions.assertThat(compressed).hasSize(96);

		{
			String decompressed = (String) preprocessor.decompress(compressed);

			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_4chars() throws IOException {
		String page = Strings.repeat("Beni", 16);
		Assertions.assertThat(page.getBytes(StandardCharsets.UTF_8)).hasSize(64);

		byte[] compressed = (byte[]) preprocessor.compress(page);

		Assertions.assertThat(compressed).hasSize(64);

		{
			String decompressed = (String) preprocessor.decompress(compressed);

			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBasic_4chars_HighSurrogate() throws IOException {
		String page = Strings.repeat("Benĩ", 16);
		Assertions.assertThat(page.getBytes(StandardCharsets.UTF_8)).hasSize(80);

		byte[] compressed = (byte[]) preprocessor.compress(page);

		Assertions.assertThat(compressed).hasSize(72);

		{
			String decompressed = (String) preprocessor.decompress(compressed);

			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}
}
