package eu.solven.hutter_prize.kanzi_only;

import java.io.IOException;

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
		Assertions.assertThat(page).hasSize(5482);

		byte[] compressed = (byte[]) preprocessor.compress(page);
		// Assertions.assertThat(compressed).hasSize(5510);

		{
			String decompressed = (String) preprocessor.decompress(compressed);
			// String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}
}
