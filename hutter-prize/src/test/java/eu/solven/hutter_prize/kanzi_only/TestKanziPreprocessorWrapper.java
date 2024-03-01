package eu.solven.hutter_prize.kanzi_only;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.IReversibleCompressor;
import eu.solven.pepper.resource.PepperResourceHelper;
import kanzi.transform.BWT;

public class TestKanziPreprocessorWrapper {
	final IReversibleCompressor preprocessor = new KanziPreprocessorWrapper(new BWT());

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Googol");
		Assertions.assertThat(page.getBytes(StandardCharsets.UTF_8)).hasSize(5509);

		byte[] compressed = (byte[]) preprocessor.compress(page);
		// A prefix byte holds the flag if the compressor has been executed or not
		Assertions.assertThat(compressed).hasSize(5510);

		{
			String decompressed = (String) preprocessor.decompress(compressed);
			// String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}
}
