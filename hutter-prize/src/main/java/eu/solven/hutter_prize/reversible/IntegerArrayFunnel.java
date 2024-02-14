package eu.solven.hutter_prize.reversible;

import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;

public enum IntegerArrayFunnel implements Funnel<int[]> {
	INSTANCE;

	@Override
	public void funnel(int[] from, PrimitiveSink into) {
		for (int someInt : from) {
			into.putInt(someInt);
		}
	}

	@Override
	public String toString() {
		return "Funnels.integerFunnel()";
	}
}