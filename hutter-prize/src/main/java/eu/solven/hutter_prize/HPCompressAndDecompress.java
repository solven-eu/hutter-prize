package eu.solven.hutter_prize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import eu.solven.hutter_prize.kanzi_only.KanziCompressor;
import eu.solven.hutter_prize.reversible.Phd9Preprocessor;
import eu.solven.hutter_prize.reversible.SentenceStartsWithUCPreprocessor;
import eu.solven.hutter_prize.reversible.SymbolsAutoClose;
import eu.solven.hutter_prize.reversible.enwik.HeaderArticlesFooter;
import eu.solven.hutter_prize.reversible.enwik.XmlToColumnarPreprocessor;
import eu.solven.hutter_prize.reversible.extract_language.AlphabetSomePreprocessor;
import eu.solven.hutter_prize.reversible.extract_language.ImageLowercaseRefPreprocessor;
import eu.solven.hutter_prize.reversible.extract_language.ImageRefPreprocessor;
import eu.solven.hutter_prize.reversible.extract_language.MathPreprocessor;
import eu.solven.hutter_prize.reversible.extract_language.TableHtmlPreprocessor;
import eu.solven.hutter_prize.reversible.extract_language.TableMarkdownAliasPreprocessor;
import eu.solven.hutter_prize.reversible.extract_language.UrlAliasPreprocessor;
import eu.solven.hutter_prize.reversible.serialization.SerializingFSTCompressor;
import eu.solven.hutter_prize.reversible.utilities.CompositeReversibleCompressor;
import eu.solven.hutter_prize.reversible.utilities.PersistingInterceptor;
import eu.solven.hutter_prize.reversible.utilities.ZipToByteArray;

public class HPCompressAndDecompress {
	private static final Logger LOGGER = LoggerFactory.getLogger(HPCompressAndDecompress.class);

	private static boolean DEBUG = false;

	static final List<String> languageWeirdAlphabet = Arrays.asList("ko",
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
			"tr",
			"be");

	static final List<IReversibleCompressor> compressors = ImmutableList.<IReversibleCompressor>builder()
			.add(

					// new ZipToByteArray(),
					new HeaderArticlesFooter(),

					// `MathPreprocessor` discards mathematical formulas, freeing a lot of small words
					new MathPreprocessor(),
					new UrlAliasPreprocessor(),
					new ImageRefPreprocessor(),
					new ImageLowercaseRefPreprocessor(),

					new TableMarkdownAliasPreprocessor(),
					new TableHtmlPreprocessor())
			.add(languageWeirdAlphabet.stream()
					.map(l -> new AlphabetSomePreprocessor(l))
					.toArray(AlphabetSomePreprocessor[]::new)

			// We replace many individual `AlphabetSomePreprocessor` by a single AlphabetManyPreprocessor. It is
			// faster
			// (as single pass) and it impacts the text vector just the same. The output file is different as it
			// would
			// mix all languages: it is acceptable as long as we do not compress the otherAlphabet file.
			// new AlphabetManyPreprocessor(languageWeirdAlphabet),
			)
			.add(
					// After alphabets as they rely on the `[[al:Youpi]]` syntax
					// new SkipClosingBrackets(),

					// `ColumnRepresentation` turn the file into columns, grouping text, ids, authors, etc
					new XmlToColumnarPreprocessor(),

					// Phd9 may be commented as it makes files less human-readable, which is painful during development
					// phase
					// `Phd9Preprocessor` clean the input, for instance encoding HTML like `&amp;`
					// We prefer Phd9Preprocessor to be applied only on the text column
					// new CountMinSketchPreprocessor(),
					// new PrePhd9Preprocessor(),
					new Phd9Preprocessor(),
					// new Phd9AdvancedPreprocessor(),

					// Turns `My name is Benoit` into `my name is Benoit`, facilitating word-autocompletion
					new SentenceStartsWithUCPreprocessor(),
					// Turns `[[Title]]\n` into `[[Title\n`
					new SymbolsAutoClose()

			// new CharactersBlockAnalysisPreprocessor(),

			// new StemAnalysisPreprocessor(),

			// new ExtractGrammarFromTextPreprocessor(),

			// new WordAnalysisPreprocessor(),
			// new CharacterAnalysisPreprocessor(),
			// new LexicalFieldPreprocessor(),

			// This would escape `<` into `<<`
			// This would escape `>` into `>>`
			// new AutocompleteWholeWordPreprocessor(128),
			// This would escape `>\w` into `>>\w`
			// new AutocompleteStemmingPreprocessor(),

			// new CharacterEncodingPreprocessor(),
			// new BWTPreprocessor(),

			// new CompressColumns(),

			// We do not apply `PackCharactersPreprocessor` after `ColumnRepresentation`
			// Else common words like `the` would be encoded differently, preventing further compression
			// mechanisms
			// new PackCharactersPreprocessor(),
			)
			.add(

					// Persist raw files (human-friendly)
					new PersistingInterceptor())
			.add(new SerializingFSTCompressor(false),
					// kanzi.app.BlockCompressor.getTransformAndCodec(int)
					new KanziCompressor(9, false),
					// Persist after Kanzi compression
					new PersistingInterceptor())
			.build();
	static final IReversibleCompressor compressor = new CompositeReversibleCompressor(compressors, DEBUG);

	public static void main(String[] args) throws IOException {
		IReversibleCompressor compressors = HPCompressAndDecompress.compressor;

		Object initialInput = HPUtils.zipped;

		// We remove the initial ZIP impact from the analysis
		byte[] original = (byte[]) new ZipToByteArray().compress(initialInput);

		Object compressed = compressors.compress(original);
		LOGGER.info("{} compressed into {}", HPUtils.nameAndSize(original), HPUtils.nameAndSize(compressed));

		byte[] decompressed = (byte[]) compressors.decompress(compressed);
		LOGGER.info("{} decompressed from {}", HPUtils.nameAndSize(decompressed), HPUtils.nameAndSize(compressed));

		sanityChecks(original, decompressed);
	}

	public static void sanityChecks(byte[] original, byte[] decompressed) {
		boolean koMiddle = false;
		String before = new String(original, StandardCharsets.UTF_8);
		String after = new String(decompressed, StandardCharsets.UTF_8);
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
