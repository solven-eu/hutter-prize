package eu.solven.hutter_prize.reversible;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import it.unimi.dsi.fastutil.ints.AbstractInt2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import kanzi.ByteTransform;
import kanzi.SliceByteArray;
import kanzi.transform.BWTS;

/**
 * Apply BWT
 * 
 * @author Benoit Lacelle
 *
 */
public class BWTPreprocessor extends AStringColumnEditorPreprocessor {

	private static final int MAX_ASCII = 0x007F;
	// As we will manipulate bytes, we are happy to handle up to 255 characters
	// However, it means the output can not be manipulated as a String anymore, as 128-255 bytes are not valid UTF8
	// private static final int MAX_ASCII = 255;
	final ByteTransform bwts = new BWTS();

	@Override
	protected String compressString(Map<String, ?> context, int index, String string) {
		Optional<EscapingResult> escaped;
		{
			byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
			if (bytes.length > string.length()) {
				escaped = escapeNotAscii(string);

				if (escaped.isEmpty()) {
					// We failed escaping the input
					return "BWT" + string;
				}

				string = doEscape(string, escaped);
			} else {
				escaped = Optional.empty();
			}
		}
		byte[] bytes = string.getBytes(StandardCharsets.UTF_8);

		SliceByteArray input = new SliceByteArray(bytes, 0);
		SliceByteArray output = new SliceByteArray(new byte[input.length], 0);

		bwts.forward(input, output);

		String outputAsString;

		if (escaped.isPresent()) {
			Int2IntMap notAsciiToAscii = escaped.get().notAsciiToAscii;

			outputAsString = unescape(output, notAsciiToAscii);
		} else {
			outputAsString = new String(output.array, StandardCharsets.UTF_8);
		}

		if (outputAsString.startsWith("BWT")) {
			// Else this would be interpreted as a String skipping BWT
			throw new IllegalArgumentException("TODO Escape this case");
		}
		return outputAsString;
	}

	private String unescape(SliceByteArray output, Int2IntMap notAsciiToAscii) {
		Int2IntMap asciiToNotAscii = new Int2IntOpenHashMap();
		((AbstractInt2IntFunction) asciiToNotAscii).defaultReturnValue(-1);
		notAsciiToAscii.int2IntEntrySet().forEach(e -> asciiToNotAscii.put(e.getIntValue(), e.getIntKey()));

		String outputAsString;
		int[] codePoints = new int[output.length];
		for (int i = 0; i < codePoints.length; i++) {
			byte rawCodePoint = output.array[i];
			int notEscapedCodePoint = rawCodePoint;
			int escapedCodepoint;
			if (asciiToNotAscii.containsKey(notEscapedCodePoint)) {
				escapedCodepoint = asciiToNotAscii.get(notEscapedCodePoint);
			} else {
				escapedCodepoint = notEscapedCodePoint;
			}
			codePoints[i] = escapedCodepoint;
		}
		outputAsString = new String(codePoints, 0, codePoints.length);
		return outputAsString;
	}

	private String doEscape(String string, Optional<EscapingResult> escaped) {
		Int2IntMap notAsciiToAscii = escaped.get().notAsciiToAscii;
		int[] onlyAscii = string.codePoints().map(codePoint -> {
			if (notAsciiToAscii.containsKey(codePoint)) {
				return notAsciiToAscii.get(codePoint);
			} else {
				// This is an ASCII character
				return codePoint;
			}
		}).toArray();

		return new String(onlyAscii, 0, onlyAscii.length);
	}

	private static class EscapingResult {
		final Int2IntMap notAsciiToAscii;

		public EscapingResult(Int2IntMap notAsciiToAscii) {
			this.notAsciiToAscii = notAsciiToAscii;
		}

	}

	private Optional<EscapingResult> escapeNotAscii(String string) {
		// int[] codePoints = string.codePoints().toArray();
		// for (int codePointIndex = 0; codePointIndex < codePoints.length; codePointIndex++) {
		// int codePoint = codePoints[codePointIndex];
		//
		// // U+007F
		// // if (codePoint > 15 + 7 * 16) {
		// // System.out.println(codePoint + " at "
		// // + codePointIndex
		// // + " "
		// // + Character.toString(string.codePointAt(codePointIndex)));
		// // }
		//
		// // System.out.println(string);
		// }

		Int2IntMap codePointToCount = new Int2IntOpenHashMap();
		((AbstractInt2IntFunction) codePointToCount).defaultReturnValue(-1);

		string.codePoints().forEach(codePoint -> codePointToCount.put(codePoint, 1 + codePointToCount.get(codePoint)));

		IntSet usedAscii = new IntOpenHashSet();
		IntSet usedNotAscii = new IntOpenHashSet();
		codePointToCount.int2IntEntrySet().forEach(e -> {
			int codePoint = e.getIntKey();

			if (codePoint > MAX_ASCII) {
				usedNotAscii.add(codePoint);
			} else {
				usedAscii.add(codePoint);
			}
		});

		Int2IntMap notAsciiToAscii = new Int2IntOpenHashMap();
		((AbstractInt2IntFunction) notAsciiToAscii).defaultReturnValue(-1);

		IntIterator notAsciiIterator = usedNotAscii.iterator();
		IntStream.rangeClosed(0, MAX_ASCII).filter(ascii -> !usedAscii.contains(ascii)).forEach(ascii -> {
			if (notAsciiIterator.hasNext()) {
				int notAscii = notAsciiIterator.nextInt();

				notAsciiToAscii.put(notAscii, ascii);
			} else {
				// no more needing replacement
			}
		});

		if (notAsciiIterator.hasNext()) {
			// There is too many not-ASCII characters needing replacement
			return Optional.empty();
		}

		return Optional.of(new EscapingResult(notAsciiToAscii));
	}

	@Override
	protected String decompressString(Map<String, ?> context, int index, String string) {
		if (string.startsWith("BWT")) {
			return string.substring("BWT".length());
		}

		Optional<EscapingResult> escaped;
		{
			byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
			if (bytes.length > string.length()) {
				escaped = escapeNotAscii(string);

				if (escaped.isEmpty()) {
					throw new IllegalStateException("Should have been caught in `compress`");
				}

				string = doEscape(string, escaped);
			} else {
				escaped = Optional.empty();
			}
		}

		byte[] compressed = string.getBytes(StandardCharsets.UTF_8);

		SliceByteArray input = new SliceByteArray(compressed, 0);
		SliceByteArray output = new SliceByteArray(new byte[input.length], 0);

		bwts.inverse(input, output);

		String outputAsString;

		if (escaped.isPresent()) {
			Int2IntMap notAsciiToAscii = escaped.get().notAsciiToAscii;

			outputAsString = unescape(output, notAsciiToAscii);
		} else {
			outputAsString = new String(output.array, StandardCharsets.UTF_8);
		}

		return outputAsString;
	}

}
