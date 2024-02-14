package eu.solven.hutter_prize.recurrent_java.trainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import eu.solven.hutter_prize.recurrent_java.autodiff.Graph;
import eu.solven.hutter_prize.recurrent_java.datastructs.DataSequence;
import eu.solven.hutter_prize.recurrent_java.datastructs.DataSet;
import eu.solven.hutter_prize.recurrent_java.datastructs.DataStep;
import eu.solven.hutter_prize.recurrent_java.loss.Loss;
import eu.solven.hutter_prize.recurrent_java.matrix.Matrix;
import eu.solven.hutter_prize.recurrent_java.model.Model;

public class Trainer {
	public static TrainingStatus train(int trainingEpochs,
			double learningRate,
			final Model model,
			DataSet data,
			int reportEveryNthEpoch
			// , boolean initFromSaved
			// , boolean overwriteSaved
			// , String savePath
			,
			Random rng) throws Exception {
		TrainingStatus trainingStatus = new TrainingStatus();
		TrainingParameters trainingParams = new TrainingParameters();

		System.out.println("--------------------------------------------------------------");
		// if (initFromSaved) {
		// System.out.println("initializing model from saved state...");
		// try {
		// model = (Model)FileIO.deserialize(savePath);
		// data.DisplayReport(model, rng);
		// }
		// catch (Exception e) {
		// System.out.println("Oops. Unable to load from a saved state.");
		// System.out.println("WARNING: " + e.getMessage());
		// System.out.println("Continuing from freshly initialized model instead.");
		// }
		// }
		for (int epoch = 0; epoch < trainingEpochs; epoch++) {

			String show = "epoch[" + (epoch + 1) + "/" + trainingEpochs + "]";

			double reportedLossTrain = pass(learningRate,
					model,
					data.training,
					true,
					data.lossTraining,
					data.lossReporting,
					trainingParams);
			trainingStatus.reportedLossTrain = reportedLossTrain;
			if (Double.isNaN(reportedLossTrain) || Double.isInfinite(reportedLossTrain)) {
				throw new Exception("WARNING: invalid value for training loss. Try lowering learning rate.");
			}
			double reportedLossValidation = 0;
			double reportedLossTesting = 0;
			if (data.validation != null) {
				reportedLossValidation = pass(learningRate,
						model,
						data.validation,
						false,
						data.lossTraining,
						data.lossReporting,
						trainingParams);
				trainingStatus.reportedLossValidation = reportedLossValidation;
			}
			if (data.testing != null) {
				reportedLossTesting = pass(learningRate,
						model,
						data.testing,
						false,
						data.lossTraining,
						data.lossReporting,
						trainingParams);
				trainingStatus.reportedLossTesting = reportedLossTesting;
			}
			show += "\ttrain loss = " + String.format("%.5f", reportedLossTrain);
			if (data.validation != null) {
				show += "\tvalid loss = " + String.format("%.5f", reportedLossValidation);
			}
			if (data.testing != null) {
				show += "\ttest loss  = " + String.format("%.5f", reportedLossTesting);
			}
			System.out.println(show);

			if (epoch % reportEveryNthEpoch == reportEveryNthEpoch - 1) {
				data.DisplayReport(model, rng);
			}

			// if (overwriteSaved) {
			//// FileIO.serialize(savePath, model);
			// refModel.set(model);
			// }

			trainingStatus.epoch = epoch;

			if (reportedLossTrain == 0 && reportedLossValidation == 0) {
				// This would be a perfect model
				System.out.println("--------------------------------------------------------------");
				System.out.println("\nDONE.");
				break;
			}
		}
		return trainingStatus;
	}

	public static double pass(double learningRate,
			Model model,
			List<DataSequence> sequences,
			boolean applyTraining,
			Loss lossTraining,
			Loss lossReporting,
			TrainingParameters p) throws Exception {

		double numerLoss = 0;
		double denomLoss = 0;

		for (DataSequence seq : sequences) {
			model.resetState();
			Graph g = new Graph(applyTraining);
			for (DataStep step : seq.steps) {
				Matrix output = model.forward(step.input, g);
				if (step.targetOutput != null) {
					double loss = lossReporting.measure(output, step.targetOutput);
					if (Double.isNaN(loss) || Double.isInfinite(loss)) {
						return loss;
					}
					numerLoss += loss;
					denomLoss++;
					if (applyTraining) {
						lossTraining.backward(output, step.targetOutput);
					}
				}
			}
			List<DataSequence> thisSequence = new ArrayList<>();
			thisSequence.add(seq);
			if (applyTraining) {
				// backprop dw values
				g.backward();
				// update params
				updateModelParams(model, learningRate, p);
			}
		}
		return numerLoss / denomLoss;
	}

	public static void updateModelParams(Model model, double stepSize, TrainingParameters p) throws Exception {
		for (Matrix m : model.getParameters()) {
			for (int i = 0; i < m.w.length; i++) {

				// rmsprop adaptive learning rate
				double mdwi = m.dw[i];
				m.stepCache[i] = m.stepCache[i] * p.decayRate + (1 - p.decayRate) * mdwi * mdwi;

				// gradient clip
				if (mdwi > p.gradientClipValue) {
					mdwi = p.gradientClipValue;
				}
				if (mdwi < -p.gradientClipValue) {
					mdwi = -p.gradientClipValue;
				}

				// update (and regularize)
				m.w[i] += -stepSize * mdwi / Math.sqrt(m.stepCache[i] + p.smoothEpsilon) - p.regularization * m.w[i];
				m.dw[i] = 0;
			}
		}
	}
}
