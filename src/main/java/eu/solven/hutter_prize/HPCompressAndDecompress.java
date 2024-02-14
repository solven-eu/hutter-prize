package eu.solven.hutter_prize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.reversible.AlphabetManyPreprocessor;
import eu.solven.hutter_prize.reversible.AutocompleteStemmingPreprocessor;
import eu.solven.hutter_prize.reversible.AutocompleteWholeWordPreprocessor;
import eu.solven.hutter_prize.reversible.CharacterAnalysisPreprocessor;
import eu.solven.hutter_prize.reversible.CharacterEncodingPreprocessor;
import eu.solven.hutter_prize.reversible.ColumnRepresentation;
import eu.solven.hutter_prize.reversible.HeaderArticlesFooter;
import eu.solven.hutter_prize.reversible.ImageLowercaseRefPreprocessor;
import eu.solven.hutter_prize.reversible.ImageRefPreprocessor;
import eu.solven.hutter_prize.reversible.MathPreprocessor;
import eu.solven.hutter_prize.reversible.PersistingCompressor;
import eu.solven.hutter_prize.reversible.Phd9Preprocessor;
import eu.solven.hutter_prize.reversible.SentenceStartsWithUCPreprocessor;
import eu.solven.hutter_prize.reversible.SkipClosingBrackets;
import eu.solven.hutter_prize.reversible.TableHtmlPreprocessor;
import eu.solven.hutter_prize.reversible.TableMarkdownPreprocessor;
import eu.solven.hutter_prize.reversible.UrlPreprocessor;
import eu.solven.hutter_prize.reversible.WordAnalysisPreprocessor;
import eu.solven.hutter_prize.reversible.ZipToByteArray;

public class HPCompressAndDecompress {
	private static final Logger LOGGER = LoggerFactory.getLogger(HPCompressAndDecompress.class);

	private static boolean DEBUG = false;

	static final IReversibleCompressor compressor = new CompositeReversibleCompressor(Arrays.asList(
			// new ZipToByteArray(),
			new HeaderArticlesFooter(),
			// `MathPreprocessor` discards mathematical formulas, freeing a lot of small words
			new MathPreprocessor(),
			new UrlPreprocessor(),
			new ImageRefPreprocessor(),
			new ImageLowercaseRefPreprocessor(),

			new TableMarkdownPreprocessor(),
			new TableHtmlPreprocessor(),

			// new AlphabetSomePreprocessor("ko"),
			// new AlphabetSomePreprocessor("ja"),
			// new AlphabetSomePreprocessor("zh"),
			// new AlphabetSomePreprocessor("zh-min-nan"),
			// new AlphabetSomePreprocessor("ar"),
			// new AlphabetSomePreprocessor("ru"),
			// new AlphabetSomePreprocessor("uk"),
			// new AlphabetSomePreprocessor("el"),
			// new AlphabetSomePreprocessor("bg"),
			// new AlphabetSomePreprocessor("bn"),
			// new AlphabetSomePreprocessor("he"),
			// new AlphabetSomePreprocessor("os"),
			// new AlphabetSomePreprocessor("fa"),
			// new AlphabetSomePreprocessor("hi"),
			// new AlphabetSomePreprocessor("th"),
			// new AlphabetSomePreprocessor("mk"),
			// new AlphabetSomePreprocessor("ka"),
			// new AlphabetSomePreprocessor("sa"),
			// new AlphabetSomePreprocessor("yi"),
			// new AlphabetSomePreprocessor("ta"),
			// new AlphabetSomePreprocessor("gu"),
			// new AlphabetSomePreprocessor("sr"),
			// new AlphabetSomePreprocessor("vi"),
			// new AlphabetSomePreprocessor("tr"),

			// We replace many individual `AlphabetSomePreprocessor` by a single AlphabetManyPreprocessor. It is faster
			// (as single pass) and it impacts the text vector just the same. The output file is different as it would
			// mix all languages: it is acceptable as long as we do not compress the otherAlphabet file.
			new AlphabetManyPreprocessor("ko",
					"ja",
					"zh",
					"zh-min-nan",
					"ar",
					"ru",
					"uk",
					"el",
					"bg",
					"bn",
					"he",
					"os",
					"fa",
					"hi",
					"th",
					"mk",
					"ka",
					"sa",
					"yi",
					"ta",
					"gu",
					"sr",
					"vi",
					"tr"),

			// After alphabets as they rely on the `[[al:Youpi]]` syntax
			new SkipClosingBrackets(),

			// `ColumnRepresentation` turn the file into columns, grouping text, ids, authors, etc
			new ColumnRepresentation(),

			// Phd9 may be commented as it makes files less human-readable, which is painful during development phase
			// `Phd9Preprocessor` clean the input, for instance encoding HTML like `&amp;`
			// We prefer Phd9Preprocessor to be applied only on the text column
			// new CountMinSketchPreprocessor(),
			// new PrePhd9Preprocessor(),
			new Phd9Preprocessor(),
			// new Phd9AdvancedPreprocessor(),

			new WordAnalysisPreprocessor(),
			new CharacterAnalysisPreprocessor(),
			// new LexicalFieldPreprocessor(),

			// This will turn `My name is Benoit` into `my name is Benoit`, facilitating word-autocompletion
			new SentenceStartsWithUCPreprocessor(),
			// This would escape `<` into `<<`
			// This would escape `>` into `>>`
			new AutocompleteWholeWordPreprocessor(128),
			// This would escape `>\w` into `>>\w`
			new AutocompleteStemmingPreprocessor(),

			// new CharacterEncodingPreprocessor(),
			// new BWTPreprocessor(),

			// new CompressColumns(),

			new PersistingCompressor()), DEBUG);

	public static void main(String[] args) throws IOException {
		IReversibleCompressor compressors = HPCompressAndDecompress.compressor;

		Object initialInput = HPUtils.zipped;

		// We remove the initial ZIP impact from the analysis
		Object initialInputPreProcesses = new ZipToByteArray().compress(initialInput);

		Object compressed = compressors.compress(initialInputPreProcesses);
		LOGGER.info("{} compressed into {}",
				HPUtils.nameAndSize(initialInputPreProcesses),
				HPUtils.nameAndSize(compressed));

		Object decompressed = compressors.decompress(compressed);
		LOGGER.info("{} decompressed from {}", HPUtils.nameAndSize(decompressed), HPUtils.nameAndSize(compressed));

		boolean koMiddle = false;
		String before = new String((byte[]) initialInputPreProcesses, StandardCharsets.UTF_8);
		String after = new String((byte[]) decompressed, StandardCharsets.UTF_8);
		for (int i = 0; i < before.length(); i++) {
			if (i > after.length()) {
				koMiddle = true;
				String aroundTail = before.substring(i - 100, Math.min(before.length(), i + 100));
				LOGGER.info("KO as AFTER is cut around {}", aroundTail);
				break;
			}

			if (before.charAt(i) != after.charAt(i)) {
				LOGGER.info("KO around character index={}", i);

				koMiddle = true;

				int beforeKo = Math.max(0, i - 100);
				int afterKoBefore = Math.min(before.length(), i + 100);
				int afterKoAfter = Math.min(after.length(), i + 100);

				String commonPrefix = HPUtils.encodeWhitespaceCharacters(before.substring(beforeKo, i)) + "->";
				LOGGER.info("KO Before: {}",
						commonPrefix + HPUtils.encodeWhitespaceCharacters(
								before.charAt(i) + "<-" + before.substring(i + 1, afterKoBefore)));
				LOGGER.info("KO After:  {}",
						commonPrefix + HPUtils.encodeWhitespaceCharacters(
								after.charAt(i) + "<-" + after.substring(i + 1, afterKoAfter)));

				break;
			}
		}

		boolean koEnd = false;
		if (!koMiddle) {
			if (after.length() > before.length()) {
				koEnd = true;
				LOGGER.info("KO After has an unexpected tail: {}",
						after.substring(before.length(), Math.min(before.length() + 100, after.length())));
			}
		}

		if (!koMiddle && !koEnd) {
			LOGGER.info("This is a SUCCESS");
		}
	}
}
