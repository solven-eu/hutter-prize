package eu.solven.hutter_prize.recurrent_java;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import eu.solven.hutter_prize.recurrent_java.datasets.EmbeddedReberGrammar;
import eu.solven.hutter_prize.recurrent_java.datastructs.DataSet;
import eu.solven.hutter_prize.recurrent_java.model.Model;
import eu.solven.hutter_prize.recurrent_java.trainer.Trainer;
import eu.solven.hutter_prize.recurrent_java.util.NeuralNetworkHelper;

public class ExampleEmbeddedReberGrammar {
	public static void main(String[] args) throws Exception {

		Random rng = new Random();
		
		DataSet data = new EmbeddedReberGrammar(rng);
		
		int hiddenDimension = 12;
		int hiddenLayers = 1;
		double learningRate = 0.001;
		double initParamsStdDev = 0.08;

		Model nn = NeuralNetworkHelper.makeLstm( 
				data.inputDimension,
				hiddenDimension, hiddenLayers, 
				data.outputDimension, data.getModelOutputUnitToUse(), 
				initParamsStdDev, rng);
		
		int reportEveryNthEpoch = 10;
		int trainingEpochs = 1000;
		
		Trainer.train(trainingEpochs, learningRate, nn, data, reportEveryNthEpoch, rng, new AtomicReference<>());
		
		System.out.println("done.");
	}
}
