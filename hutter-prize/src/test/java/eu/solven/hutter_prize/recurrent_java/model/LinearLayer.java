package eu.solven.hutter_prize.recurrent_java.model;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import eu.solven.hutter_prize.recurrent_java.autodiff.Graph;
import eu.solven.hutter_prize.recurrent_java.matrix.Matrix;


public class LinearLayer implements Model {

	private static final long serialVersionUID = 1L;
	Matrix W;
	//no biases
	
	public LinearLayer(int inputDimension, int outputDimension, double initParamsStdDev, Random rng) {
		W = Matrix.rand(outputDimension, inputDimension, initParamsStdDev, rng);
	}
	
	@Override
	public Matrix forward(Matrix input, Graph g) throws Exception {
		Matrix out = g.mul(W, input);
		return out;
	}

	@Override
	public void resetState() {

	}

	@Override
	public List<Matrix> getParameters() {
		List<Matrix> result = new ArrayList<>();
		result.add(W);
		return result;
	}
}
