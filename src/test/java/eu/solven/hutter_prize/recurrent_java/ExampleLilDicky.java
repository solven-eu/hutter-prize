package eu.solven.hutter_prize.recurrent_java;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import eu.solven.hutter_prize.recurrent_java.datasets.TextGenerationUnbroken;
import eu.solven.hutter_prize.recurrent_java.datastructs.DataSet;
import eu.solven.hutter_prize.recurrent_java.model.Model;
import eu.solven.hutter_prize.recurrent_java.trainer.Trainer;
import eu.solven.hutter_prize.recurrent_java.util.NeuralNetworkHelper;

public class ExampleLilDicky {
	public static void main(String[] args) throws Exception {

		Random rng = new Random();
		int totalSequences = 2000;
		int sequenceMinLength = 10;
		int sequenceMaxLength = 100;
		String textSource = "LilDicky";
		DataSet data = new TextGenerationUnbroken("datasets/text/" + textSource
				+ ".txt", totalSequences, sequenceMinLength, sequenceMaxLength, rng);
		String savePath = "saved_models/" + textSource + ".ser";
		boolean initFromSaved = true; // set this to false to start with a fresh model
		boolean overwriteSaved = true;

		TextGenerationUnbroken.reportSequenceLength = 500;

		int bottleneckSize = 10; // one-hot input is squeezed through this
		int hiddenDimension = 200;
		int hiddenLayers = 1;
		double learningRate = 0.001;
		double initParamsStdDev = 0.08;

		Model lstm = NeuralNetworkHelper.makeLstmWithInputBottleneck(data.inputDimension,
				bottleneckSize,
				hiddenDimension,
				hiddenLayers,
				data.outputDimension,
				data.getModelOutputUnitToUse(),
				initParamsStdDev,
				rng);

		int reportEveryNthEpoch = 10;
		int trainingEpochs = 1000;

		Trainer.train(trainingEpochs,
				learningRate,
				lstm,
				data,
				reportEveryNthEpoch,
//				initFromSaved,
//				overwriteSaved,
//				savePath,
				rng);

		System.out.println("done.");
	}
}
