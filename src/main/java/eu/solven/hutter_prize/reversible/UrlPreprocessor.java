package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import eu.solven.hutter_prize.IReversibleCompressor;

/**
 * We extract URL as they are generally not nice english grammar, and kind of follow its own grammar (e.g. no space)).
 * 
 * @author Benoit Lacelle
 *
 */
public class UrlPreprocessor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(UrlPreprocessor.class);

	private static final Pattern PATTERN_URL_WORDS = Pattern.compile("\\[url\\d+");

	// We handle a subset of potential scheme
	final String openUrl = "\\[http://";
	// Either the URL is closed by `]`, or the link is couple with a nice sentence, separated of the URL by a ` `
	final String closeUrl = "[\\] ]";

	@Override
	public Object compress(Object input) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) input;

		String pages = (String) asMap.get("body");

		Map.Entry<String, Map<String, String>> compressed = processString(pages);

		LOGGER.info("Input without formulas has been turned from {} to {}",
				pages.length(),
				compressed.getKey().length());

		// Make sure we let transit other information in other fields
		Map<String, Object> output = new LinkedHashMap<>(asMap);

		// Write an updated body
		output.put("body", compressed.getKey());

		// We write formulas in a separate location
		output.put("urls", compressed.getValue());

		return output;
	}

	private Map.Entry<String, Map<String, String>> processString(String string) {

		AtomicInteger mathIndex = new AtomicInteger();
		Map<String, String> shortToUrl = new HashMap<>();

		// Collect all `urlXXX` words once and for all
		Set<String> mathWordsAlreadyPresent = new HashSet<>();
		PATTERN_URL_WORDS.matcher(string).results().forEach(mr -> mathWordsAlreadyPresent.add(mr.group()));

		// https://javascript.info/regexp-greedy-and-lazy
		Matcher matcher = Pattern.compile("(" + openUrl + "([^\\] ]+)" + closeUrl + ")").matcher(string);
		// matcher.results().forEach(mr -> {
		// String formula = mr.group(1);
		// });

		String replacedString = matcher.replaceAll(mr -> {
			String matched = mr.group();
			String url = matched.substring("[".length(), matched.length() - "]".length());

			// Find an index so that the word does not exist
			while (mathWordsAlreadyPresent.contains("url" + mathIndex.get())) {
				mathIndex.incrementAndGet();
			}

			String shortcut = "url" + mathIndex.getAndIncrement();

			// String formula = rawMath.substring(openUrl.length(), rawMath.length() - closeUrl.length());
			shortToUrl.put(shortcut, url);
			// String shortcut = shortToUrl.get(url);

			// We need to restore the trailing ` ` or `]`
			return "[" + shortcut + matched.substring(matched.length() - 1);
		});

		LOGGER.info("We detected {} URLs. {} distinct",
				shortToUrl.size(),
				shortToUrl.values().stream().distinct().count());

		return Maps.immutableEntry(replacedString, shortToUrl);
	}

	@Override
	public Object decompress(Object output) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) output;

		String pages = asMap.get("body").toString();

		// Make sure we let transit other information in other fields
		Map<String, Object> input = new LinkedHashMap<>(asMap);

		// We extracted part of `body` into `math`
		input.remove("urls");
		input.put("body", restoreMath(pages, (Map<String, String>) asMap.get("urls")));

		return input;
	}

	private String restoreMath(String string, Map<String, String> mathToShortcut) {
		// for (Entry<String, String> entry : mathToShortcut.entrySet()) {
		// String shortcut = entry.getValue();
		// String math = entry.getKey();
		// string = string.replaceAll(Pattern.quote(shortcut), Matcher.quoteReplacement(math));
		// }

		String replacedString = PATTERN_URL_WORDS.matcher(string).replaceAll(mr -> {
			String math = mr.group();
			String shortcut = math.substring("[".length());

			String url = mathToShortcut.get(shortcut);

			if (url == null) {
				// This was a `mathXXX` word already present in the original input
				return math;
			}

			return Matcher.quoteReplacement("[" + url);
		});

		return replacedString;
	}

}
