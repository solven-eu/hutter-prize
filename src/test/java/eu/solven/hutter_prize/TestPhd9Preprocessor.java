package eu.solven.hutter_prize;

import java.util.Map;
import java.util.stream.IntStream;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;

import eu.solven.hutter_prize.reversible.Phd9Preprocessor;

public class TestPhd9Preprocessor {
	@Test
	public void testSimplifyString() {
		Assertions.assertThat(Phd9Preprocessor.compactWords("aaa")).isEqualTo("w");
		Assertions.assertThat(Phd9Preprocessor.compactWords("a b")).isEqualTo("z");

		Assertions.assertThat(Phd9Preprocessor.compactWords(" aaa")).isEqualTo(" w");
		Assertions.assertThat(Phd9Preprocessor.compactWords(" a b")).isEqualTo(" z");

		Assertions.assertThat(Phd9Preprocessor.compactWords("aaa ")).isEqualTo("w ");
		Assertions.assertThat(Phd9Preprocessor.compactWords("a b ")).isEqualTo("z ");

		Assertions.assertThat(Phd9Preprocessor.compactWords(" aaa ")).isEqualTo(" w ");
		Assertions.assertThat(Phd9Preprocessor.compactWords(" a b ")).isEqualTo(" z ");

		Assertions.assertThat(Phd9Preprocessor.compactWords("[[aaa]]")).isEqualTo("[[w]]");
		Assertions.assertThat(Phd9Preprocessor.compactWords("[[a b]]")).isEqualTo("[[z]]");

		Assertions.assertThat(Phd9Preprocessor.compactWords("[[ aaa ]]")).isEqualTo("[[ w ]]");
		Assertions.assertThat(Phd9Preprocessor.compactWords("[[ a b ]]")).isEqualTo("[[ z ]]");

		Assertions.assertThat(Phd9Preprocessor.compactWords("a b c. a b c [[")).isEqualTo("z [[");
		Assertions.assertThat(Phd9Preprocessor.compactWords("a b. c d. e f.")).isEqualTo("z.");
		Assertions.assertThat(Phd9Preprocessor.compactWords("a b. c d. e f. ")).isEqualTo("z. ");

		Assertions.assertThat(Phd9Preprocessor.compactWords("a b c, a b c [[")).isEqualTo("z [[");
		Assertions.assertThat(Phd9Preprocessor.compactWords("a b, c d, e f.")).isEqualTo("z.");
		Assertions.assertThat(Phd9Preprocessor.compactWords("a b, c d, e f. ")).isEqualTo("z. ");

		// Assertions.assertThat(Phd9Preprocessor.compactWords("Je m'appele Benoit.")).isEqualTo("z. ");
		Assertions.assertThat(Phd9Preprocessor.compactWords("Je suis Benoit. Benoit, est la")).isEqualTo("z");
		Assertions.assertThat(Phd9Preprocessor.compactWords("Pierre, Paul, Jacques et tatie sont la")).isEqualTo("z");
		Assertions.assertThat(Phd9Preprocessor.compactWords("Je suis la avec Pierre, Paul, Jacques et tatie"))
				.isEqualTo("z");
	}

	@Test
	public void testCanSpare() {
		Assertions.assertThat(Phd9Preprocessor.canSpare("[[]]", 123)).isEqualTo(123 * 3);

		// Optimal pattern: `#w` -> ` [[w]]`
		// It means a single character can be used to infer a prefix and a suffix
		Assertions.assertThat(Phd9Preprocessor.canSpare("[[w]]", 123)).isEqualTo(123 * 3);

		// Optimal pattern: `z#w` -> ` z [[w]]`
		// It means we need to separate words groups to restore some prefix, suffix and separator
		// It means we can save the prefix, the suffix and limit each separator to size 1
		Assertions.assertThat(Phd9Preprocessor.canSpare(" z [[w]]", 123)).isEqualTo(123 * 4);
		Assertions.assertThat(Phd9Preprocessor.canSpare(" z [[w", 123)).isEqualTo(123 * 2);
		Assertions.assertThat(Phd9Preprocessor.canSpare("z [[w", 123)).isEqualTo(123);
	}

	@Test
	public void testHPUtils() {
		// Beware there is weird UTF8 characters in the input
		Assertions.assertThat(HPUtils.encodeWhitespaceCharacters(" w")).isEqualTo(" {127}w{128}");

		// for (int i = 0; i < 1024; i++) {
		// System.out.println(HPUtils.encodeWhitespaceCharacters(Character.toString(i)));
		// }
	}

	@Test
	public void testReplaceThem_whitespacePrefix() {
		Map<String, String> replaceThem = ImmutableMap.<String, String>builder()
				// Opening pattern, whitespace version is second
				.put(" [[", "(")
				.put("[[", "{")

				// Closing pattern, whitespace version is first
				.put("]] ", ")")
				.put("]]", "}")
				.build();

		Assertions.assertThat(Phd9Preprocessor.replaceHC(replaceThem, "[[A]]")).isEqualTo("{A}");
		Assertions.assertThat(Phd9Preprocessor.replaceHC(replaceThem, " [[A]] ")).isEqualTo("(A)");

		{
			BiMap<String, String> reversed = ImmutableBiMap.copyOf(replaceThem).inverse();

			Assertions.assertThat(Phd9Preprocessor.replaceHC(reversed, "{A}")).isEqualTo("[[A]]");
			Assertions.assertThat(Phd9Preprocessor.replaceHC(reversed, "(A)")).isEqualTo(" [[A]] ");
		}
	}

	@Test
	public void testReplaceThem_amp() {
		Map<String, String> replaceThem =
				ImmutableMap.<String, String>builder().put("&gt;", ">").put("&amp;", "&").build();

		Assertions.assertThat(Phd9Preprocessor.replaceHC(replaceThem, "&amp;")).isEqualTo("&");
		Assertions.assertThat(Phd9Preprocessor.replaceHC(replaceThem, "&gt;")).isEqualTo(">");

		Assertions.assertThat(Phd9Preprocessor.replaceHC(replaceThem, "&amp;gt;")).isEqualTo("&gt;");

		{
			Map<String, String> reversed = Phd9Preprocessor.reverseReplaceThem(replaceThem);

			Assertions.assertThat(Phd9Preprocessor.replaceHC(reversed, "&")).isEqualTo("&amp;");
			Assertions.assertThat(Phd9Preprocessor.replaceHC(reversed, ">")).isEqualTo("&gt;");

			Assertions.assertThat(Phd9Preprocessor.replaceHC(reversed, "&gt;")).isEqualTo("&amp;gt;");
		}
	}

	@Test
	public void testReplaceThem_hc() {
		// 65 is A, 90 is Z
		Map<String, String> replaceThem = Phd9Preprocessor.hcReplaceThem(IntStream.rangeClosed(65, 90).iterator());

		Phd9Preprocessor.checkIsSafe(replaceThem);
	}

	@Test
	public void testCheckIsSafe() {
		Map<String, String> replaceThem =
				ImmutableMap.<String, String>builder().put("&amp;", "&").put("&gt;", ">").build();

		Assertions.assertThatThrownBy(() -> Phd9Preprocessor.checkIsSafe(replaceThem))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
