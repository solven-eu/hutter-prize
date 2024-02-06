package eu.solven.hutter_prize.recurrent_java.model;

import java.io.Serializable;

public interface Nonlinearity extends Serializable {
	double forward(double x);

	double backward(double x);
}
