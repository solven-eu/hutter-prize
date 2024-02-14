package eu.solven.hutter_prize.kanzi_only;

import java.io.IOException;

import org.nustaq.serialization.FSTConfiguration;

import eu.solven.hutter_prize.IReversibleCompressor;

/**
 * Pack some Java object into a byte[]
 * 
 * @author Benoit Lacelle
 *
 */
public class SerializingFSTCompressor implements IReversibleCompressor {
	// https://github.com/RuedigerMoeller/fast-serialization/wiki/Serialization
	final static FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

	@Override
	public Object compress(Object input) throws IOException {
		return conf.asByteArray(input);
	}

	@Override
	public Object decompress(Object output) throws IOException {
		return conf.asObject((byte[]) output);
	}

}
