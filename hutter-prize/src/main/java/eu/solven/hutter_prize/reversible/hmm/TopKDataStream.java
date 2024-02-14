package eu.solven.hutter_prize.reversible.hmm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * 
 * @author Benoit Lacelle
 *
 * @param <K>
 *            needs to implement a stable hashCode/equals
 */
public class TopKDataStream<K> {
	// The maximum number of topK elements we would maintain
	final int maxDistinct;

	// This tracks occurences.size()
	final AtomicLong currentSum = new AtomicLong();
	final AtomicLongMap<K> occurences = AtomicLongMap.create();
	// This is a random-access to `occurences.keySet()`
	final List<K> listAccepted;
	// How many word-occurrences are not being tracked
	final AtomicLong trashCount = new AtomicLong();

	final Random r;

	public TopKDataStream(int maxCount, Random r) {
		this.maxDistinct = maxCount;
		this.r = r;

		this.listAccepted = new ArrayList<K>(maxCount);
	}

	public void offer(K value) {
		if (occurences.size() < maxDistinct) {
			// We have not yet encountered enough elements
			long newCount = occurences.incrementAndGet(value);
			if (newCount == 1) {
				listAccepted.add(value);
			}
			currentSum.incrementAndGet();
		} else {
			if (occurences.get(value) >= 1) {
				// This value is already known
				occurences.incrementAndGet(value);
				currentSum.incrementAndGet();
			} else {
				// This is a new value: it has a chance to take the room from a previously registered item
				long registeredCount = currentSum.get();
				long thrashedCount = trashCount.get();

				// Throw a coin to decide if this is considered trash of potential new high-probable entry
				long nextLong = r.nextLong(registeredCount + thrashedCount);
				if (nextLong < thrashedCount) {
					// Let's trash this input
					trashCount.incrementAndGet();
				} else {
					long cumulativeHit = nextLong - thrashedCount;

					// We would want to do -1 on the key holding cumulativeHit occurrence
					// But it is expensive to maintain such a structure
					// So we will rely on a faster method, even though it is biased
					// So we will pick a random item along the accepted ones

					int itemIndex = (int) (cumulativeHit % maxDistinct);

					K mayBeTrashed = listAccepted.get(itemIndex);
					long newCount = occurences.decrementAndGet(mayBeTrashed);
					if (newCount == 0) {
						// This entry is to be replaced by the input
						occurences.incrementAndGet(value);
						listAccepted.set(itemIndex, value);
						
						trashCount.incrementAndGet();
					}
				}
			}
		}
	}

	public AtomicLongMap<K> getTopK() {
		// Clone
		return AtomicLongMap.create(occurences.asMap());
	}
}
