package eu.solven.hutter_prize.reversible;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import eu.solven.hutter_prize.IReversibleCompressor;

/**
 * This decompresses an input ZIP. It makes the current state bigger, but easier to later compress. It is some sort of
 * facilitating preprocessor, like Burrows-Wheeler.
 * 
 * @author Benoit Lacelle
 *
 */
public class ZipToByteArray implements IReversibleCompressor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ZipToByteArray.class);

	@Override
	public Object compress(Object input) throws IOException {
		Resource asResource = (Resource) input;

		try (ZipInputStream zipInputStream = new ZipInputStream(asResource.getInputStream())) {
			ZipEntry nextEntry = zipInputStream.getNextEntry();

			if (nextEntry == null) {
				LOGGER.info("{} is empty", asResource.getURI());
				return new byte[0];
			}

			LOGGER.info(" Entry: {}", nextEntry.getName());

			byte[] bytes = zipInputStream.readAllBytes();

			if (null != zipInputStream.getNextEntry()) {
				LOGGER.info("There is more than one entry. Only the first one is considered. {}", asResource.getURI());
			}

			return bytes;
		}
	}

	@Override
	public Object decompress(Object output) throws IOException {
		byte[] bytes = (byte[]) output;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(baos)) {
			zipOutputStream.putNextEntry(new ZipEntry("single_file"));

			zipOutputStream.write(bytes);

			zipOutputStream.closeEntry();
		}

		return baos.toByteArray();
	}

}
