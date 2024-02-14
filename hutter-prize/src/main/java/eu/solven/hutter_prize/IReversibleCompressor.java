package eu.solven.hutter_prize;

import java.io.IOException;

/**
 * Describe a compressing step, which is reversible. A pipeline of {@link IReversibleCompressor} is also a
 * {@link IReversibleCompressor}
 * 
 * @author Benoit Lacelle
 *
 */
public interface IReversibleCompressor {
	Object compress(Object input) throws IOException;

	Object decompress(Object output) throws IOException;
}
