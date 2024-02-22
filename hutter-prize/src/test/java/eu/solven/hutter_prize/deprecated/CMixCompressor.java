package eu.solven.hutter_prize.deprecated;

import java.io.IOException;

import ai.djl.nn.recurrent.LSTM;
import eu.solven.hutter_prize.IReversibleCompressor;

/**
 * Based on [CMix](https://www.byronknoll.com/cmix.html)
 * 
 * @author Benoit Lacelle
 *
 *         MacOS installation: `brew install cmix`
 */
// https://github.com/deepjavalibrary/djl/blob/master/examples/src/main/java/ai/djl/examples/training/TrainMnistWithLSTM.java
public class CMixCompressor implements IReversibleCompressor {

	@Override
	public Object compress(Object input) throws IOException {
		LSTM lstm = LSTM.builder().build();

		// lstm.

		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object decompress(Object output) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
