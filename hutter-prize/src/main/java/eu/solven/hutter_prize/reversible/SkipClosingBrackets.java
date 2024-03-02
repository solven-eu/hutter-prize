package eu.solven.hutter_prize.reversible;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Turns `[[SomeWord]]` into `[[8SomeWord`
 * 
 * @author Benoit Lacelle
 * @see SymbolsAutoClose
 */
@Deprecated(since = "SymbolsAutoClose is better")
public class SkipClosingBrackets extends AStringColumnEditorPreprocessor {
	private static final String ESCAPE = "_";

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		Pattern leadingDigits = Pattern.compile("(\\d+).*");

		return Pattern.compile("\\[\\[([^\\]]+)([\\]]{0,2})(?!\\d)").matcher(string).replaceAll(mr -> {
			String enclosed = mr.group(1);
			String closing = mr.group(2);

			if (enclosed.startsWith(ESCAPE)) {
				throw new IllegalStateException("Change the escaping policy");
			}

			Matcher leadingDigitsMatcher = leadingDigits.matcher(enclosed);

			if (leadingDigitsMatcher.matches() || !"]]".equals(closing)) {
				// We have leading digits: these needs to be escaped
				// String leadingNumber = leadingDigitsMatcher.group(1);
				// `[[2002]]`
				// `[[2word]]s`
				return Matcher.quoteReplacement("[[" + ESCAPE + enclosed + closing);
			}

			if (enclosed.length() >= 100) {
				// We do not want to pay 3 digits to spare `]]`
				return Matcher.quoteReplacement("[[" + ESCAPE + enclosed + closing);
			}

			return Matcher.quoteReplacement("[[" + enclosed.length() + enclosed);
		});

	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		AtomicInteger nbRemoved = new AtomicInteger();
		IntList positionToInsert = new IntArrayList();

		Matcher matcher = Pattern.compile("(?<=\\[\\[)(" + ESCAPE + "?)(\\d*)").matcher(string);
		String stripped = replaceAll(string, matcher, mr -> {
			String isEscaped = mr.group(1);
			String digits = mr.group(2);

			if (!isEscaped.isEmpty()) {
				assert ESCAPE.equals(isEscaped);

				// We remove the escaping character, without adding `]]`
				nbRemoved.incrementAndGet();
				return digits;
			}

			if (digits.isEmpty()) {
				// UnitTest this case
				return "";
			}

			int localNbRemoved = nbRemoved.addAndGet(digits.length());

			// Remember the position to insert, adjusted by the fact we removed N previous characters
			int newPosition = mr.end() + Integer.parseInt(digits) - localNbRemoved;

			// string.substring(mr.start() - 100, mr.end() + 100)

			if (!positionToInsert.isEmpty() && newPosition < positionToInsert.getInt(positionToInsert.size() - 1)) {
				throw new IllegalStateException("We are decreasing");
			}

			positionToInsert.add(newPosition);

			// We drop the digits
			return "";
		});

		StringBuilder sb = new StringBuilder();
		AtomicInteger previousEnd = new AtomicInteger(0);

		positionToInsert.forEach(i -> {
			sb.append(stripped.subSequence(previousEnd.getAndSet(i), i));
			sb.append("]]");
		});

		sb.append(stripped.subSequence(previousEnd.get(), stripped.length()));

		return sb.toString();
	}

	public String replaceAll(String text, Matcher m, Function<MatchResult, String> replacer) {
		Objects.requireNonNull(replacer);
		m.reset();
		boolean result = m.find();
		if (result) {
			StringBuilder sb = new StringBuilder();
			do {
				String replacement = replacer.apply(m);
				m.appendReplacement(sb, replacement);
				result = m.find();
			} while (result);
			m.appendTail(sb);
			return sb.toString();
		}
		return text;
	}
}
