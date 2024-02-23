package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.nustaq.serialization.FSTConfiguration;

import eu.solven.hutter_prize.reversible.analysis.CharactersBlockAnalysisPreprocessor;
import eu.solven.pepper.collection.PepperMapHelper;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestCharactersBlockAnalysisPreprocessor {
	final static FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

	final IReversibleCompressor preprocessor = new CharactersBlockAnalysisPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		Assertions.assertThat(page).hasSize(5482);
		Assertions.assertThat(conf.asByteArray(page)).hasSize(5528);

		Map<String, ?> compressed = (Map<String, ?>) preprocessor.compress(Map.of("body", page));
		Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "body")).hasSize(3516);
		Assertions.assertThat(conf.asByteArray(compressed)).hasSize(5117);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preprocessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testRepetitive() throws IOException {
		String page = "a10 000 000 000 000 000 000 000 000 000 000 000 000";
		Map<String, String> wrappedPage = Map.of("body", page);
		Assertions.assertThat(conf.asByteArray(wrappedPage)).hasSize(86);

		Object compressed = preprocessor.compress(wrappedPage);
		Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "body")).isEqualTo("a10  0  0  0");
		Assertions.assertThat(conf.asByteArray(compressed)).hasSize(223);

		Object decompressed = preprocessor.decompress(compressed);

		Assertions.assertThat(decompressed).isEqualTo(wrappedPage);
	}
}
