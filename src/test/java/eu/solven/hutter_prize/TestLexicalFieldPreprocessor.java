package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.LexicalFieldPreprocessor;
import eu.solven.pepper.collection.PepperMapHelper;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestLexicalFieldPreprocessor {
	final LexicalFieldPreprocessor preProcessor = new LexicalFieldPreprocessor();

	@Test
	public void testAbuDhabi() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Abu Dhabi");
		Assertions.assertThat(page).doesNotContain("w0").contains("Abu Dhabi");

		Map<String, ?> compressed =
				(Map<String, ?>) preProcessor.compress(Map.of("keyToVector", Map.of("text", Arrays.asList(page))));

		Assertions.assertThat(compressed).containsKeys("keyToContext-" + preProcessor.getClass().getSimpleName());

		Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "keyToVector", "text", 0))
				.contains("w0")
				.doesNotContain("Abu Dhabi");

		Assertions.assertThat(PepperMapHelper
				.getRequiredAs(compressed, "keyToContext-LexicalFieldPreprocessor", "text", "wordDictionaries")
				.toString()).contains("Abu Dhabi");

		{
			Map<String, ?> decompressed = (Map<String, ?>) preProcessor.decompress(compressed);
			String decompressedBody = PepperMapHelper.getRequiredString(decompressed, "keyToVector", "text", 0);

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}
}
