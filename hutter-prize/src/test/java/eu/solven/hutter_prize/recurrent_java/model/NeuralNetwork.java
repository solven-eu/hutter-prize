package eu.solven.hutter_prize.recurrent_java.model;

import java.util.ArrayList;
import java.util.List;

import eu.solven.hutter_prize.recurrent_java.autodiff.Graph;
import eu.solven.hutter_prize.recurrent_java.matrix.Matrix;

public class NeuralNetwork implements Model {

	private static final long serialVersionUID = 1L;
	List<Model> layers = new ArrayList<>();
	
	public NeuralNetwork(List<Model> layers) {
		this.layers = layers;
	}
	
	@Override
	public Matrix forward(Matrix input, Graph g) throws Exception {
		Matrix prev = input;
		for (Model layer : layers) {
			prev = layer.forward(prev, g);
		}
		return prev;
	}

	@Override
	public void resetState() {
		for (Model layer : layers) {
			layer.resetState();
		}
	}

	@Override
	public List<Matrix> getParameters() {
		List<Matrix> result = new ArrayList<>();
		for (Model layer : layers) {
			result.addAll(layer.getParameters());
		}
		return result;
	}
}
