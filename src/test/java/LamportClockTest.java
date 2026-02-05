import org.junit.jupiter.api.Test;
import utilize.LamportClock;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LamportClockTest {
    @Test
    void testLamportClockSend() {
        LamportClock clock = new LamportClock();

        long send = clock.onSend();
        assertEquals(1, send);
    }

    @Test
    void testLamportClockReceive() {
        LamportClock clock = new LamportClock();

        long receive = clock.onReceive(5);
        assertEquals(6, receive);
    }

    @Test
    void testLamportClockGetter() {
        LamportClock clock = new LamportClock();

        clock.onSend();
        long newTimestamp = clock.get();
        assertEquals(1, newTimestamp);
    }

    @Test
    void testLamportClockSetter() {
        LamportClock clock = new LamportClock();

        clock.set(5);
        long newTimestamp = clock.get();
        assertEquals(5, newTimestamp);
    }
}
