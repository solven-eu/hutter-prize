package eu.solven.hutter_prize.recurrent_java.loss;

import java.io.Serializable;

import eu.solven.hutter_prize.recurrent_java.matrix.Matrix;

public interface Loss extends Serializable {
	void backward(Matrix actualOutput, Matrix targetOutput) throws Exception;

	double measure(Matrix actualOutput, Matrix targetOutput) throws Exception;
}
