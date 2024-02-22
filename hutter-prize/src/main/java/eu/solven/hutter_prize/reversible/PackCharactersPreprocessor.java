package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.stream.IntStream;

import eu.solven.hutter_prize.reversible.utilities.AVisitingCompressor;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.lemire.integercompression.BinaryPacking;
import me.lemire.integercompression.Composition;
import me.lemire.integercompression.IntWrapper;
import me.lemire.integercompression.VariableByte;

/**
 * This will turn each character into its index in a symbol table, and pack the indexes given the cardinality of symbol
 * table.
 * 
 * Typically `abcabc` is turned into `012012` and, given a cardinality of 3, hence a `1<<2` bits are necessary per
 * index, packed into `000110000110`.
 * 
 * TODO Implement some optimal VariableByte encoding. e.g.
 * https://pages.di.unipi.it/rossano/assets/pdf/papers/TKDE20.pdf
 * 
 * @author Benoit Lacelle
 *
 */
public class PackCharactersPreprocessor extends AVisitingCompressor<String, byte[]> {
	// BinaryPacking will not compress the data, making it still easy to compress. It operates as sort of ZRLT
	// BinaryPacking consumes only blocks of size 32: it needs to be coupled with another encoding handling the rest
	final Composition binaryPacking = new Composition(new BinaryPacking(), new VariableByte());

	public PackCharactersPreprocessor() {
		super(String.class, byte[].class);
	}

	@Override
	protected byte[] compressString(String string) throws IOException {
		Int2IntMap codePointToIndex = new Int2IntOpenHashMap();

		string.codePoints().forEach(codePoint -> codePointToIndex.putIfAbsent(codePoint, codePointToIndex.size()));

		IntBuffer intBuffer;
		{

			int[] indexedCodePoints = string.codePoints().map(codePoint -> codePointToIndex.get(codePoint)).toArray();

			// Pack the dictionary and the indexes codePoints
			{
				intBuffer = IntBuffer.allocate(1 + 2 * codePointToIndex.size() + indexedCodePoints.length);

				// Write the dictionary
				intBuffer.put(codePointToIndex.size());
				codePointToIndex.int2IntEntrySet().forEach(e -> {
					intBuffer.put(e.getIntKey());
					intBuffer.put(e.getIntValue());
				});

				intBuffer.put(indexedCodePoints);
			}
		}

		IntWrapper inpos = new IntWrapper();

		// We target the compressed structure to be smaller than the input structure
		int[] compressed = new int[string.length()];

		IntWrapper outpos = new IntWrapper();
		binaryPacking.compress(intBuffer.array(), inpos, intBuffer.capacity(), compressed, outpos);

		if (inpos.get() != intBuffer.capacity()) {
			throw new IllegalStateException("Not all input has been consumed");
		}

		// 4 byte per int
		// 1 leading for the output size
		ByteBuffer byteBuffer = ByteBuffer.allocate((1 + outpos.get()) * 4);

		IntBuffer asIntBuffer = byteBuffer.asIntBuffer();

		asIntBuffer.put(intBuffer.capacity());

		// Write the compressed ints
		asIntBuffer.put(compressed, 0, outpos.get());

		return byteBuffer.array();
	}

	@Override
	protected String decompressString(byte[] bytes) throws IOException {
		IntBuffer bb = ByteBuffer.wrap(bytes).asIntBuffer();

		int compressedInts = bb.get();
		int[] decompressed;

		{
			int[] compressed = new int[bb.capacity() - 1];
			bb.get(compressed);

			decompressed = new int[compressedInts];
			IntWrapper inpos = new IntWrapper();

			// BinaryPacking starts by writing the number of decompressed ints
			// +32 for the left-overs
			// int[] indexedCodePoints = new int[compressedIndexesCodePoints[0] + 32];
			IntWrapper outpos = new IntWrapper();
			binaryPacking.uncompress(compressed, inpos, compressed.length, decompressed, outpos);

			if (outpos.get() != compressedInts) {
				throw new IllegalStateException();
			}
		}

		IntBuffer decompressedBB = IntBuffer.wrap(decompressed);
		int indexSize = decompressedBB.get();

		Int2IntMap indexToCodepoint = new Int2IntOpenHashMap(indexSize);
		for (int i = 0; i < indexSize; i++) {
			int codePoint = decompressedBB.get();
			int index = decompressedBB.get();
			indexToCodepoint.put(index, codePoint);
		}

		int intForDic = (1 + 2 * indexSize);
		int intForIndexes = decompressed.length - intForDic;

		int[] indexedCodePoints = new int[intForIndexes];
		decompressedBB.get(indexedCodePoints);

		int[] codePoints = IntStream.of(indexedCodePoints).map(index -> indexToCodepoint.get(index)).toArray();

		return new String(codePoints, 0, codePoints.length);
	}

}
