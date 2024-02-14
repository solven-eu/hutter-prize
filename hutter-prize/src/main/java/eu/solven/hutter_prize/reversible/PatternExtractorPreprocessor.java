package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.google.common.collect.Maps;

import eu.solven.hutter_prize.IReversibleCompressor;

/**
 * PHD9 was Hutter Prize winner in 2018, and some of its logic is still used by latest top-entries. We re-implement here
 * related logics.
 * 
 * @author Benoit Lacelle
 *
 */
// https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h
public class PatternExtractorPreprocessor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(PatternExtractorPreprocessor.class);

	final String rawPatternWords;
	private final Pattern patternWords;

	final String openMath;
	final String closeMath;

	final String mapKey;

	public PatternExtractorPreprocessor(String patternWords, String openMath, String closeMath, String mapKey) {
		this.rawPatternWords = patternWords;
		this.patternWords = Pattern.compile(patternWords);
		this.openMath = openMath;
		this.closeMath = closeMath;

		this.mapKey = mapKey;

		if (StringUtils.countOccurrencesOf(patternWords, "\\d+") != 1) {
			throw new IllegalArgumentException("Invalid regex: " + patternWords);
		}
	}

	@Override
	public Object compress(Object input) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) input;

		String pages = (String) asMap.get("body");

		// Collect all `mathXXX` words once and for all
		Map.Entry<String, Map<String, String>> compressed = processString(pages);

		LOGGER.info("Input with `{}` has been turned from length={} to length={}",
				mapKey,
				pages.length(),
				compressed.getKey().length());

		// Make sure we let transit other information in other fields
		Map<String, Object> output = new LinkedHashMap<>(asMap);

		// Write an updated body
		output.put("body", compressed.getKey());

		// We write formulas in a separate location
		output.put(mapKey, compressed.getValue());

		return output;
	}

	private Map.Entry<String, Map<String, String>> processString(String string) {

		AtomicInteger mathIndex = new AtomicInteger();
		Map<String, String> shortcutToFormula = new HashMap<>();

		Set<String> mathWordsAlreadyPresent = lookForExistingShortcuts(patternWords, string);

		// https://javascript.info/regexp-greedy-and-lazy
		Matcher matcher = Pattern.compile(openMath + "(.*?)" + closeMath, Pattern.DOTALL).matcher(string);

		String replacedString = matcher.replaceAll(mr -> {
			String rawMath = mr.group();

			// String formula = mr.group(1);

			String shortcut = popShortcut(mathIndex, mathWordsAlreadyPresent);

			String cleanPrefix = regexToQuoted(openMath);
			String cleanSuffix = regexToQuoted(closeMath);
			String formula = rawMath.substring(cleanPrefix.length(), rawMath.length() - cleanSuffix.length());
			shortcutToFormula.put(shortcut, formula);

			return shortcut;
		});

		LOGGER.info("We detected {} {} entries. {} distinct",
				shortcutToFormula.size(),
				mapKey,
				shortcutToFormula.values().stream().distinct().count());

		return Maps.immutableEntry(replacedString, shortcutToFormula);
	}

	public static Set<String> lookForExistingShortcuts(Pattern pattern, String string) {
		Set<String> mathWordsAlreadyPresent = new HashSet<>();
		pattern.matcher(string).results().forEach(mr -> mathWordsAlreadyPresent.add(mr.group()));
		return mathWordsAlreadyPresent;
	}

	// https://stackoverflow.com/questions/12423071/how-to-remove-escape-characters-from-a-string-in-java
	protected String regexToQuoted(String regex) {
		return regex.replace("\\[", "[")
				.replace("\\]", "]")
				.replace("\\{", "{")
				.replace("\\}", "}")
				.replace("\\|", "|");
	}

	private String popShortcut(AtomicInteger mathIndex, Set<String> mathWordsAlreadyPresent) {
		int indexDigits = rawPatternWords.indexOf("\\d+");

		String prefix = regexToQuoted(rawPatternWords.substring(0, indexDigits));
		String suffix = regexToQuoted(rawPatternWords.substring(indexDigits + "\\d+".length()));

		// Find an index so that the word does not exist
		while (mathWordsAlreadyPresent.contains(prefix + mathIndex.get() + suffix)) {
			mathIndex.incrementAndGet();
		}

		String shortcut = prefix + mathIndex.getAndIncrement() + suffix;
		return shortcut;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) output;

		String pages = asMap.get("body").toString();

		// Make sure we let transit other information in other fields
		Map<String, Object> input = new LinkedHashMap<>(asMap);

		// We extracted part of `body` into `math`
		input.remove(mapKey);
		input.put("body", restoreMath(pages, (Map<String, String>) asMap.get(mapKey)));

		return input;
	}

	private String restoreMath(String string, Map<String, String> shortcutToFormula) {
		// for (Entry<String, String> entry : mathToShortcut.entrySet()) {
		// String shortcut = entry.getValue();
		// String math = openMath + entry.getKey() + closeMath;
		// string = string.replaceAll(Pattern.quote(shortcut), Matcher.quoteReplacement(math));
		// }

		String replacedString = patternWords.matcher(string).replaceAll(mr -> {
			String shortcut = mr.group();

			String formula = shortcutToFormula.get(shortcut);

			if (formula == null) {
				// This was a `mathXXX` word already present in the original input
				return shortcut;
			}

			String cleanPrefix = regexToQuoted(openMath);
			String cleanSuffix = regexToQuoted(closeMath);
			return Matcher.quoteReplacement(cleanPrefix + formula + cleanSuffix);
		});

		return replacedString;
	}

}
