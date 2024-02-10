package eu.solven.hutter_prize;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TestCompressNonEnglishAlphabets {
	@Test
	public void testChinese() {
		String c = "[[zh:阿布扎比]]";

		Assertions.assertThat(c.getBytes(StandardCharsets.UTF_8)).hasSize(19);

		// https://stackoverflow.com/questions/1366068/whats-the-complete-range-for-chinese-characters-in-unicode
		int minChinese = c.codePoints().min().getAsInt();
		int maxChinese = c.codePoints().max().getAsInt();

		System.out.println(Arrays.toString(c.codePoints().toArray()));
	}

	@Test
	public void testKorean() {
		String c = "[[ko:아부다비]]";

		Assertions.assertThat(c.getBytes(StandardCharsets.UTF_8)).hasSize(19);

		// https://stackoverflow.com/questions/1366068/whats-the-complete-range-for-chinese-characters-in-unicode
		int minChinese = c.codePoints().min().getAsInt();
		int maxChinese = c.codePoints().max().getAsInt();

		System.out.println(Arrays.toString(c.codePoints().toArray()));
	}
}
