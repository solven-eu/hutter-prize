package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.ExtractGrammarFromTextPreprocessor;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestExtractGrammarFromTextPreprocessor {
	final IReversibleCompressor preprocessor = new ExtractGrammarFromTextPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");

		Map.Entry<String, List<String>> compressed = (Map.Entry<String, List<String>>) preprocessor.compress(page);

		Assertions.assertThat(compressed.getKey()).doesNotContain("==w w w==").contains("==w==");
		// Assertions.assertThat(compressed.getValue()).contains("This");

		{
			Object decompressed = preprocessor.decompress(compressed);

			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}
}
