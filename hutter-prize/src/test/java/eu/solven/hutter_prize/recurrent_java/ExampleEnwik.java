package eu.solven.hutter_prize.recurrent_java;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.HPCompressAndDecompress;
import eu.solven.hutter_prize.HPUtils;
import eu.solven.hutter_prize.IReversibleCompressor;
import eu.solven.hutter_prize.recurrent_java.datasets.TextGeneration;
import eu.solven.hutter_prize.recurrent_java.model.Model;
import eu.solven.hutter_prize.recurrent_java.trainer.Trainer;
import eu.solven.hutter_prize.recurrent_java.util.NeuralNetworkHelper;
import eu.solven.hutter_prize.reversible.utilities.ZipToByteArray;
import eu.solven.pepper.io.PepperSerializationHelper;
import eu.solven.pepper.memory.PepperMemoryHelper;

public class ExampleEnwik {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExampleEnwik.class);

	public static void main(String[] args) throws Exception {
		Object initialInput = HPUtils.zipped;

		// We remove the initial ZIP impact from the analysis
		Object initialInputPreProcesses = new ZipToByteArray().compress(initialInput);

		String asString = new String((byte[]) initialInputPreProcesses, StandardCharsets.UTF_8);

		TextGeneration data = new TextGeneration(asString);
		// String savePath = "saved_models/" + textSource + ".ser";
		boolean initFromSaved = false; // set this to false to start with a fresh model
		boolean overwriteSaved = true;

		data.reportSequenceLength = 100;
		data.singleWordAutocorrect = false; // set this to true to constrain generated sentences to contain
											// only words observed in the training data.

		int bottleneckSize = 10; // one-hot input is squeezed through this
		int hiddenDimension = 30;
		int hiddenLayers = 1;
		double learningRate = 0.001;
		double initParamsStdDev = 0.08;

		Random rng = new Random();
		Model lstm = NeuralNetworkHelper.makeLstmWithInputBottleneck(data.inputDimension,
				bottleneckSize,
				hiddenDimension,
				hiddenLayers,
				data.outputDimension,
				data.getModelOutputUnitToUse(),
				initParamsStdDev,
				rng);

		byte[] modelAsSbytes = PepperSerializationHelper.toBytes(lstm);
		LOGGER.info("Model size: {}", PepperMemoryHelper.memoryAsString(modelAsSbytes.length));

		int reportEveryNthEpoch = 10;
		int trainingEpochs = 10;

		// AtomicReference<Model> refModel = new AtomicReference<>();
		Trainer.train(trainingEpochs,
				learningRate,
				lstm,
				data,
				reportEveryNthEpoch,
				// initFromSaved,
				// overwriteSaved,
				// savePath,
				rng);

		System.out.println("done.");

		String modelAsString = PepperSerializationHelper.toString(lstm);
		String fileName = "hp-lstm-bns_" + bottleneckSize + "-hd_" + hiddenDimension + ".nn";

		// data.DisplayReport(lstm, rng);

		// refModel.get().forward(null, null)
		// double t = 0.5D;
		// List<String> generated = TextGeneration.generateText(lstm, 5, true, t, rng);
		// System.out.println(generated);
	}
}
