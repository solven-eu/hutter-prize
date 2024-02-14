package eu.solven.hutter_prize.reversible;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PHD9 will preprocess the text to simplify it. For instance, replacing `&gt;` by `>`. This preprocessor will simplify
 * the text by detecting/compressing some patterns. For instance, we known that `[[\w+]` is always followed by `]`: we
 * can them replace any `[[\w+]]` by `[[\w]`
 * 
 * @author Benoit Lacelle
 *
 */
// https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h
public class PrePhd9Preprocessor extends ASymbolsPreprocessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(PrePhd9Preprocessor.class);

	@Override
	protected Map<String, ?> analyzeList(List<?> list) {
		Map<String, String> replaceThem = new LinkedHashMap<>();

		// No due to `of the [[Trinity]&amp;#8212;, an`
		// replaceThem.put("\\[\\[\\w+\\]", "]");

		// No due to ` Server 2003. \n{{Clr}\n== Future developmen`
		// replaceThem.put("\\{\\{\\w+\\}", "}");

		int listSize = list.size();
		for (int stringIndex = 0; stringIndex < listSize; stringIndex++) {
			// Each char is attached to its int value
			Object rawInput = list.get(stringIndex);
			if (rawInput instanceof String) {
				String rawAsString = rawInput.toString();

				replaceThem.forEach((k, v) -> {
					Pattern.compile(k).matcher(rawAsString).results().filter(mr -> {
						String after = rawAsString.substring(mr.end(), mr.end() + v.length());
						if (!after.equals(v)) {
							// This is a faulty matchResult
							return true;
						}

						return false;
					}).forEach(mr -> {
						String after = rawAsString.substring(mr.end() - 20, mr.end() + v.length() + 20);
						LOGGER.warn("`{}`: `{}` is followed by {} instead of {}", k, mr.group(), after, v);
					});
				});
			}
		}
		return Map.of();
	}

}
