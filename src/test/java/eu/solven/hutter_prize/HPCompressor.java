package eu.solven.hutter_prize;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HPCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(HPCompressor.class);

	public static void main(String[] args) throws IOException {

		Object compressed = HPCompressAndDecompress.compressor.compress(HPUtils.zipped);

		LOGGER.info("{} compressed into {}", HPUtils.nameAndSize(HPUtils.zipped), HPUtils.nameAndSize(compressed));
	}
}
