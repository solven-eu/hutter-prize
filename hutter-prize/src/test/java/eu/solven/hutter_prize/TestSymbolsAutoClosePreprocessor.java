package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.SymbolsAutoClose;
import eu.solven.pepper.collection.PepperMapHelper;
import eu.solven.pepper.resource.PepperResourceHelper;

public class TestSymbolsAutoClosePreprocessor {
	final IReversibleCompressor preprocessor = new SymbolsAutoClose();

	@Test
	public void testGoogol() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Googol");
		Assertions.assertThat(page).hasSize(5482);

		Map<String, ?> compressed = (Map<String, ?>) preprocessor.compress(Map.of("body", page));
		Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "body")).hasSize(5461);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preprocessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testBeforeDot() throws IOException {
		String page = "For the Internet company, see [[Google]].";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("For the Internet company, see [[Google.");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testWithSpace() throws IOException {
		String page = "For the Internet company, see [[Google Company]].";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("For the Internet company, see [[Google Company.");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testEndOfString() throws IOException {
		String page = "For the Internet company, see [[Google]]";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("For the Internet company, see [[Google");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testMiddleOfSentence() throws IOException {
		String page = "Je [[suis]] Benoit";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("Je [[suis]] Benoit");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testPipe() throws IOException {
		String page = "the [[numerical digit|digit]] 1";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("the [[numerical digit|digit]] 1");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}
	
	

	@Test
	public void testNotClosed() throws IOException {
		String page = "For the Internet company, see [[Google Company. [[Youpi]]";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("For the Internet company, see [[Google Company!. [[Youpi");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}
}
