package eu.solven.hutter_prize.kanzi_only;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import eu.solven.hutter_prize.IReversibleCompressor;

/**
 * Pack some Java object into a byte[]
 * 
 * @author Benoit Lacelle
 *
 */
public class SerializingJavaCompressor implements IReversibleCompressor {

	@Override
	public Object compress(Object input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(input);
		}

		return baos.toByteArray();
	}

	@Override
	public Object decompress(Object output) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream((byte[]) output);

		try (ObjectInputStream ois = new ObjectInputStream(bais)) {
			try {
				return ois.readObject();
			} catch (ClassNotFoundException | IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
