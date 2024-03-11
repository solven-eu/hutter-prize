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
		// Assertions.assertThat(page).hasSize(5482);

		Map<String, ?> compressed = (Map<String, ?>) preprocessor.compress(Map.of("body", page));
		Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "body")).hasSize(5380);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preprocessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testAnarchism() throws IOException {
		String page = PepperResourceHelper.loadAsString("/pages/Anarchism");
		Assertions.assertThat(page).hasSize(12275);

		Map<String, ?> compressed = (Map<String, ?>) preprocessor.compress(Map.of("body", page));
		String compressedPage = compressed.get("body").toString();
		Assertions.assertThat(compressedPage).hasSize(12158);

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
	public void testParenthesis() throws IOException {
		String page = "followed by one hundred [[0 (number)|zero]]s.";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("followed by one hundred [[0 (number)|zero]]s.");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testColumn() throws IOException {
		String page = "[[da:Googol]]";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[da:Googol");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testColumnEol() throws IOException {
		String page = "[[da:Googol]]\n";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[da:Googol\n");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testColumnEolColumn() throws IOException {
		String page = "[[da:Googol]]\n[[fr:Google]]";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("[[da:Googol\n[[fr:Google");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testParenthesisThenComa() throws IOException {
		String page = "to ten thousand [[sexdecillion]] (or sedecillion), or ten";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("to ten thousand [[sexdecillion]] (or sedecillion), or ten");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	// @Ignore("TODO")
	@Test
	public void testConsecutive() throws IOException {
		String page = "the quiz show on [[10 September]] [[2001]].";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("the quiz show on [[10 September [[2001.");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testConsecutive_2() throws IOException {
		String page = "The Internet [[search engine]] [[Google]] was named after this number.";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed)
				.isEqualTo("The Internet [[search engine [[Google]] was named after this number.");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testEquals() throws IOException {
		String page = "==See also==";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("==See also");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testEqualsSpace() throws IOException {
		String page = "== References ==";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("== References");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testEqualsSpaceLeft() throws IOException {
		String page = "== References==";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("== References==");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testEquals_Quote() throws IOException {
		String page = "==Relation to ''-illion'' number names==";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("==Relation to ''-illion'' number names");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testQuote_triple() throws IOException {
		String page = "A '''googol''' is the";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("A '''googol''' is the");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testQuote_brackets() throws IOException {
		String page = "published ''[[What is Property?]]'' in 1840";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("published ''[[What is Property?'' in 1840!");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testQuote_bracketsDot() throws IOException {
		String page = "published ''[[What is Property?]]''.";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("published ''[[What is Property?''.");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testQuote_brackets_2() throws IOException {
		String page = "the [[Greek language|Greek]] ''[[Wiktionary:aaa]]''";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("the [[Greek language|Greek ''[[Wiktionary:aaa''");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testBracket_doubled() throws IOException {
		String page = "[[Image:BenjaminTucker.jpg|thumb|150px|left|[[Benjamin Tucker]]]]";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed)
				.isEqualTo("[[Image:BenjaminTucker!.jpg|thumb|150px|left|[[Benjamin Tucker]]]]");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testAccent() throws IOException {
		String page = "of the [[Confederación General del Trabajo]] and the";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed).isEqualTo("of the [[Confederación General del Trabajo]] and the");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testCurlySquared() throws IOException {
		String page = "{{main articles|[[Pierre-Joseph Proudhon]] and [[Mutualism (economic theory)]]}}";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed)
				.isEqualTo("{{main articles|[[Pierre-Joseph Proudhon]] and [[Mutualism (economic theory)");

		{
			String decompressed = preprocessor.decompress(compressed).toString();
			Assertions.assertThat(decompressed).isEqualTo(page);
		}
	}

	@Test
	public void testAAA() throws IOException {
		String page =
				"(or ''Ars Poetica'')\n\n==== A work outside the ''Corpus Aristotelicum'' ====\n* The [[Constitution of the Athenians]]";
		String compressed = (String) preprocessor.compress(page);

		Assertions.assertThat(compressed)
				.isEqualTo(
						"(or ''Ars Poetica'')\n\n==== A work outside the ''Corpus Aristotelicum ====\n* The [[Constitution of the Athenians");

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
