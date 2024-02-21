package eu.solven.hutter_prize.reversible.serialization;

import java.io.IOException;

import org.nustaq.serialization.FSTConfiguration;

import eu.solven.hutter_prize.reversible.utilities.AVisitingCompressor;

/**
 * Pack some Java object into a byte[]
 * 
 * @author Benoit Lacelle
 *
 */
public class SerializingFSTCompressor extends AVisitingCompressor<Object, byte[]> {
	// https://github.com/RuedigerMoeller/fast-serialization/wiki/Serialization
	final static FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

	final boolean toSingleByteArray;

	public SerializingFSTCompressor(boolean toSingleByteArray) {
		super(Object.class, byte[].class);
		this.toSingleByteArray = toSingleByteArray;
	}

	public SerializingFSTCompressor() {
		this(true);
	}

	@Override
	protected byte[] defaultCompress(Object input) {
		return conf.asByteArray(input);
	}

	@Override
	protected Object defaultDecompress(Object output) {
		return conf.asObject((byte[]) output);
	}

	@Override
	public Object compress(Object input) throws IOException {
		if (toSingleByteArray) {
			return defaultCompress(input);
		} else {
			return super.compress(input);
		}
	}

	@Override
	public Object decompress(Object input) throws IOException {
		if (toSingleByteArray) {
			return defaultDecompress(input);
		} else {
			return super.decompress(input);
		}
	}

	@Override
	protected byte[] compressString(Object string) throws IOException {
		return defaultCompress(string);
	}

	@Override
	protected Object decompressString(byte[] bytes) throws IOException {
		return defaultDecompress(bytes);
	}

}
