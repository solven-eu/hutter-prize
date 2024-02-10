package eu.solven.hutter_prize.recurrent_java.trainer;

public class TrainingStatus {
	// epoch may be smaller than initially request if we interrupt the training (e.g. too long, or good enough)
	int epoch;
	public double reportedLossTrain = Double.NaN;
	public double reportedLossValidation = Double.NaN;
	public double reportedLossTesting = Double.NaN;
}
