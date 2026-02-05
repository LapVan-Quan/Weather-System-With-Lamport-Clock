package utilize;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class LamportClock {
    private final AtomicLong counter;

    public LamportClock() {
        counter = new AtomicLong(0);
    }

    /**
     * Increment the counter
     * @return counter value
     */
    public long onSend() {
        return counter.incrementAndGet();
    }

    /**
     * Compare the local time and the received Lamport time to find max and then increment
     * @param timestamp (long)
     * @return counter value
     */
    public long onReceive(long timestamp) {
        return counter.updateAndGet(local -> Math.max(local, timestamp) + 1);
    }

    /**
     * Getter of counter
     * @return counter value
     */
    public long get() { return counter.get(); }

    public void set(long timestamp) {
        counter.updateAndGet(local -> timestamp);
    }

}
