package eu.solven.hutter_prize.reversible.analysis;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import eu.solven.hutter_prize.reversible.analysis.CharactersBlockAnalysisPreprocessor.OptimalComputer;
import eu.solven.pepper.collection.PepperMapHelper;
import eu.solven.pepper.resource.PepperResourceHelper;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public class TestCharactersBlockAnalysisPreprocessor {
	// final static FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

	final CharactersBlockAnalysisPreprocessor preprocessor = new CharactersBlockAnalysisPreprocessor();

	@Test
	public void testGoogol() throws IOException {
		// This is very slow
		if (true)
			return;
		String page = PepperResourceHelper.loadAsString("/pages/googol");
		Assertions.assertThat(page).hasSize(5482);
		// Assertions.assertThat(conf.asByteArray(page)).hasSize(5528);
		Assertions.assertThat(page.toString()).hasSize(5482);

		Map<String, ?> compressed = (Map<String, ?>) preprocessor.compress(Map.of("body", page));
		// Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "body")).hasSize(4004);
		// Assertions.assertThat(conf.asByteArray(compressed)).hasSize(5535);
		Assertions.assertThat(compressed.toString()).hasSize(4828);

		{
			Map<String, ?> decompressed = (Map<String, ?>) preprocessor.decompress(compressed);
			String decompressedBody = decompressed.get("body").toString();

			Assertions.assertThat(decompressedBody).isEqualTo(page);
		}
	}

	@Test
	public void testRepetitive_4blocks() throws IOException {
		String page = "a10 000 000 000 000 000 000 000 000 000 000 000 000";
		Map<String, String> wrappedPage = Map.of("body", page);
		// Assertions.assertThat(conf.asByteArray(wrappedPage)).hasSize(86);

		Object compressed = preprocessor.compress(wrappedPage);
		Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "body")).isEqualTo("a10 0 0 0 0 0 0");
		// Assertions.assertThat(conf.asByteArray(compressed)).hasSize(223);
		// Assertions.assertThat(compressed.toString()).hasSize(116);

		Object decompressed = preprocessor.decompress(compressed);

		Assertions.assertThat(decompressed).isEqualTo(wrappedPage);
	}

	@Test
	public void scanSubString_handleOverlaps_3a() {
		Object2LongMap<IntList> intsToCounts = new Object2LongOpenHashMap<>();
		preprocessor.scanSubStrings(2, 2, intsToCounts, "aaa");

		Assertions.assertThat(intsToCounts).hasSize(1).containsEntry(IntArrayList.of('a', 'a'), 1L);
	}

	@Test
	public void scanSubString_handleOverlaps_4a() {
		Object2LongMap<IntList> intsToCounts = new Object2LongOpenHashMap<>();
		preprocessor.scanSubStrings(2, 2, intsToCounts, "aaaa");

		Assertions.assertThat(intsToCounts).hasSize(1).containsEntry(IntArrayList.of('a', 'a'), 2L);
	}

	@Test
	public void scanSubString_handleOverlaps_5a() {
		Object2LongMap<IntList> intsToCounts = new Object2LongOpenHashMap<>();
		preprocessor.scanSubStrings(2, 2, intsToCounts, "aaaaa");

		Assertions.assertThat(intsToCounts).hasSize(1).containsEntry(IntArrayList.of('a', 'a'), 2L);
	}

	@Test
	public void scanSubString_handleOverlaps_6a() {
		Object2LongMap<IntList> intsToCounts = new Object2LongOpenHashMap<>();
		preprocessor.scanSubStrings(2, 2, intsToCounts, "aaaaaa");

		Assertions.assertThat(intsToCounts).hasSize(1).containsEntry(IntArrayList.of('a', 'a'), 3L);
	}

	@Test
	public void scanSubString_handleOverlaps_largeLong() {
		Object2LongMap<IntList> intsToCounts = new Object2LongOpenHashMap<>();
		preprocessor.scanSubStrings(2, 4, intsToCounts, "10 000 000 000 000 000 000 000 000 000 000 000 000");

		Assertions.assertThat(intsToCounts)
				.hasSize(14)
				.containsEntry(IntArrayList.of('1', '0'), 1L)
				.containsEntry(IntArrayList.of('0', ' '), 12L)
				.containsEntry(IntArrayList.of(' ', '0'), 12L)
				.containsEntry(IntArrayList.of('0', '0'), 12L)

				.containsEntry(IntArrayList.of(' ', '0', '0', '0'), 12L);

		OptimalComputer optimalComputer = new CharactersBlockAnalysisPreprocessor.OptimalComputer(intsToCounts);

		{
			long bonus = optimalComputer.getBonusLeftSide(IntList.of(' '), IntList.of('z'));
			// Negative as remove some nice ` 00` and introduce inexistent `z00`
			Assertions.assertThat(bonus).isNegative();
		}
		{
			long bonus = optimalComputer.getBonusRightSide(IntList.of(' '), IntList.of('z'));
			// Negative as remove some nice `00 ` and introduce inexistent `00z`
			Assertions.assertThat(bonus).isNegative().isEqualTo(-12);
		}

		// {
		// long bonus = optimalComputer.getBonusRightSide(IntList.of('0'), IntList.of(' '));
		// // Negative as remove some nice `0[_000]_00` and introduce inexistent `0[__]_00`
		// Assertions.assertThat(bonus).isPositive().isEqualTo(1);
		// }
	}

	@Test
	public void scanSubString_handleOverlaps_alphabet() {
		Object2LongMap<IntList> intsToCounts = new Object2LongOpenHashMap<>();

		// We should detected `cde` -> `xy` is very good
		// As it leads to `abx` -> `...` and `yfg` -> `...`
		preprocessor.scanSubStrings(2, 3, intsToCounts, "abcdefg_abx_yfg_abx_yfg_yfg");

		Assertions.assertThat(intsToCounts)
				// .hasSize(115)
				.containsEntry(IntArrayList.of('c', 'd', 'e'), 1L)
		// .containsEntry(IntArrayList.of('0', ' '), 12L)
		// .containsEntry(IntArrayList.of(' ', '0'), 12L)
		// .containsEntry(IntArrayList.of('0', '0'), 12L)
		//
		// .containsEntry(IntArrayList.of(' ', '0', '0', '0'), 12L)
		;

		OptimalComputer optimalComputer = new CharactersBlockAnalysisPreprocessor.OptimalComputer(intsToCounts);

		{
			long bonus = optimalComputer.getBonusLeftSide(IntList.of('c'), IntList.of('x'));
			Assertions.assertThat(bonus).isPositive().isEqualTo(2 - 1);
		}
		{
			long bonus = optimalComputer.getBonusRightSide(IntList.of('e'), IntList.of('y'));
			Assertions.assertThat(bonus).isPositive().isEqualTo(3 - 1);
		}
	}

	// With this pattern, we should turn `cde` into `xy`
	@Test
	public void testPlannedRecursivePattern() throws IOException {
		String page = "abcdefg_abx_yfg_abx_yfg_yfg".repeat(10);

		final CharactersBlockAnalysisPreprocessor preprocessor = new CharactersBlockAnalysisPreprocessor() {

			@Override
			protected Optional<Entry<String, String>> findNextReplacement(List<?> list,
					int depthForReplacing,
					int depth) {
				// if (list.contains(page)) {
				// return Optional.of(Map.entry("cde", "xy"));
				// }

				return super.findNextReplacement(list, depthForReplacing, depth);
			}
		};

		Map<String, String> wrappedPage = Map.of("body", page);
		// Assertions.assertThat(conf.asByteArray(wrappedPage)).hasSize(86);

		Object compressed = preprocessor.compress(wrappedPage);
		Assertions.assertThat(PepperMapHelper.getRequiredString(compressed, "body")).isEqualTo("_xf_xf_xf_xf_x__");
		// Assertions.assertThat(conf.asByteArray(compressed)).hasSize(223);
		// Assertions.assertThat(compressed.toString()).hasSize(126);

		Object decompressed = preprocessor.decompress(compressed);

		Assertions.assertThat(decompressed).isEqualTo(wrappedPage);
	}
}
