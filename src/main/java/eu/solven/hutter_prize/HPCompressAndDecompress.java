package eu.solven.hutter_prize;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.reversible.ColumnRepresentation;
import eu.solven.hutter_prize.reversible.HeaderArticlesFooter;
import eu.solven.hutter_prize.reversible.ZipToByteArray;

public class HPCompressAndDecompress {
	private static final Logger LOGGER = LoggerFactory.getLogger(HPCompressAndDecompress.class);

	static final IReversibleCompressor compressor = new CompositeReversibleCompressor(
			Arrays.asList(new ZipToByteArray(), new HeaderArticlesFooter(), new ColumnRepresentation()));

	public static void main(String[] args) throws IOException {
		IReversibleCompressor compressors = HPCompressAndDecompress.compressor;

		Object compressed = compressors.compress(HPUtils.zipped);
		LOGGER.info("{} compressed into {}", HPUtils.nameAndSize(HPUtils.zipped), HPUtils.nameAndSize(compressed));

		Object decompressed = compressors.decompress(compressed);
		LOGGER.info("{} decompressed into {}", HPUtils.nameAndSize(compressed), HPUtils.nameAndSize(decompressed));
	}
}
