package eu.solven.hutter_prize.recurrent_java.trainer;

public class TrainingParameters {

	public double decayRate = 0.999;
	public double smoothEpsilon = 1e-8;
	public double gradientClipValue = 5;
	public double regularization = 0.000001; // L2 regularization strength

}
