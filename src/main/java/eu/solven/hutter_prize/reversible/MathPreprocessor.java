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
public class MathPreprocessor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(MathPreprocessor.class);

	private static final Pattern PATTERN_MATH_WORDS = Pattern.compile("math\\(\\d+\\)");

	// Mathematical formulas holds a lot of small words, which would prevent later abbreviation (e.g. replacing
	// `because` by `bcz` is not possible if `bcz` word is consumed by a mathematical formulae
	final String openMath = "&lt;math&gt;";
	final String closeMath = "&lt;/math&gt;";

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
		output.put("math", compressed.getValue());

		return output;
	}

	private Map.Entry<String, Map<String, String>> processString(String string) {

		AtomicInteger mathIndex = new AtomicInteger();
		Map<String, String> shortcutToFormula = new HashMap<>();

		// Collect all `mathXXX` words once and for all
		Set<String> mathWordsAlreadyPresent = new HashSet<>();
		PATTERN_MATH_WORDS.matcher(string).results().forEach(mr -> mathWordsAlreadyPresent.add(mr.group()));

		// https://javascript.info/regexp-greedy-and-lazy
		Matcher matcher = Pattern.compile(openMath + "(.*)" + closeMath).matcher(string);
		// matcher.results().forEach(mr -> {
		//
		// mathToShortcut.put(formula, key);
		// });

		String replacedString = matcher.replaceAll(mr -> {
			String rawMath = mr.group();

			// String formula = mr.group(1);

			// Find an index so that the word does not exist
			while (mathWordsAlreadyPresent.contains("math(" + mathIndex.get() + ")")) {
				mathIndex.incrementAndGet();
			}

			String shortcut = "math(" + mathIndex.getAndIncrement() + ")";

			String formula = rawMath.substring(openMath.length(), rawMath.length() - closeMath.length());
			shortcutToFormula.put(shortcut, formula);

			return shortcut;
		});

		LOGGER.info("We detected {} math formulas. {} distinct",
				shortcutToFormula.size(),
				shortcutToFormula.values().stream().distinct().count());

		return Maps.immutableEntry(replacedString, shortcutToFormula);
	}

	@Override
	public Object decompress(Object output) throws IOException {
		Map<String, ?> asMap = (Map<String, ?>) output;

		String pages = asMap.get("body").toString();

		// Make sure we let transit other information in other fields
		Map<String, Object> input = new LinkedHashMap<>(asMap);

		// We extracted part of `body` into `math`
		input.remove("math");
		input.put("body", restoreMath(pages, (Map<String, String>) asMap.get("math")));

		return input;
	}

	private String restoreMath(String string, Map<String, String> shortcutToFormula) {
		// for (Entry<String, String> entry : mathToShortcut.entrySet()) {
		// String shortcut = entry.getValue();
		// String math = openMath + entry.getKey() + closeMath;
		// string = string.replaceAll(Pattern.quote(shortcut), Matcher.quoteReplacement(math));
		// }

		String replacedString = PATTERN_MATH_WORDS.matcher(string).replaceAll(mr -> {
			String shortcut = mr.group();

			String formula = shortcutToFormula.get(shortcut);

			if (formula == null) {
				// This was a `mathXXX` word already present in the original input
				return shortcut;
			}

			return Matcher.quoteReplacement(openMath + formula + closeMath);
		});

		return replacedString;
	}

}
