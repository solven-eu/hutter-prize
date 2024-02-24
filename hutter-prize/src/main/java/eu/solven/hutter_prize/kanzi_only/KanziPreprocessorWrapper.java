package eu.solven.hutter_prize.kanzi_only;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import eu.solven.hutter_prize.IReversibleCompressor;
import kanzi.ByteTransform;
import kanzi.SliceByteArray;
import kanzi.transform.BWT;
import kanzi.transform.Sequence;
import kanzi.transform.TextCodec;

public class KanziPreprocessorWrapper implements IReversibleCompressor {
	final Sequence sequence;

	public KanziPreprocessorWrapper(ByteTransform... transforms) {
		sequence = new Sequence(transforms);
	}

	public KanziPreprocessorWrapper() {
		this(new TextCodec());
	}

	@Override
	public Object compress(Object input) throws IOException {
		byte[] bytes = ((String) input).getBytes(StandardCharsets.UTF_8);

		SliceByteArray src = new SliceByteArray(bytes, 0);

		// +1 for skipFlag
		SliceByteArray dst = new SliceByteArray(new byte[1 + bytes.length], 1);

		boolean done = sequence.forward(src, dst);

		if (!done) {
			throw new IllegalStateException("Not done");
		}

		byte skipFlags = sequence.getSkipFlags();
		dst.array[0] = skipFlags;

		return Arrays.copyOfRange(dst.array, 0, 1 + dst.index);
	}

	@Override
	public Object decompress(Object output) throws IOException {
		byte[] bytes = (byte[]) output;

		SliceByteArray src = new SliceByteArray(bytes, bytes.length - 1, 1);

		SliceByteArray dst = new SliceByteArray(new byte[bytes.length], 0);

		sequence.setSkipFlags(bytes[0]);
		boolean done = sequence.inverse(src, dst);

		if (!done) {
			throw new IllegalStateException("Not done");
		}

		byte[] decompressed = Arrays.copyOfRange(dst.array, dst.index, dst.index + dst.length);
		return new String(decompressed, StandardCharsets.UTF_8);
	}

}
