package ai.flowguard.fixture.race;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Test-only synchronization for deterministic concurrency experiments.
 */
public final class RaceCheckpoint {

    public enum Point {
        AFTER_IDEMPOTENCY_LOOKUP,
        AFTER_BALANCE_READ
    }

    private static volatile Point activePoint = Point.AFTER_IDEMPOTENCY_LOOKUP;
    private static volatile CyclicBarrier barrier = new CyclicBarrier(2);
    private static volatile boolean enabled = false;

    private RaceCheckpoint() {
    }

    public static void configure(Point point, int expectedThreads) {
        activePoint = point;
        barrier = new CyclicBarrier(Math.max(2, expectedThreads));
        enabled = true;
    }

    public static void reset() {
        enabled = false;
        barrier = new CyclicBarrier(2);
    }

    public static void pauseIfEnabled(Point point) {
        if (!enabled || activePoint != point) {
            return;
        }
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Race checkpoint timed out", e);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Race checkpoint interrupted", e);
        }
    }

    /** Allows external runner to disable barrier after experiment. */
    public static void releaseAll() {
        enabled = false;
    }
}
