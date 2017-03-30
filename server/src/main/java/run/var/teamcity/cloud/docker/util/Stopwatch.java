package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * A simple stopwatch implementation with nano-seconds precision. Any time conversion will be performed according to
 * the {@link TimeUnit} contract.
 * <p>
 *     This implementation is thread safe.
 * </p>
 */
public class Stopwatch {

    private volatile long startTimeNanos;

    private Stopwatch() {
        reset();
    }

    /**
     * Reset this stopwatch. Bring this stopwatch back to 0, and start counting immediately.
     */
    public void reset() {
        startTimeNanos = System.nanoTime();
    }

    /**
     * Creates a new stopwatch instance and start counting time.
     *
     * @return the new stopwatch instance
     */
    public static Stopwatch start() {
        return new Stopwatch();
    }

    /**
     * Executes a runnable synchronously and returns the execution time in millis.
     *
     * @param runnable the runnable to execute
     *
     * @return the measure execution time in milliseconds
     *
     * @throws NullPointerException if {@code runnable} is {@code null}
     */
    public static long measureMillis(@Nonnull Runnable runnable) {
        DockerCloudUtils.requireNonNull(runnable, "Runnable cannot be null.");
        return measure(runnable, TimeUnit.MILLISECONDS);
    }

    private static long measure(Runnable runnable, TimeUnit unit) {
        assert runnable != null && unit != null;
        Stopwatch sw = start();
        runnable.run();
        return unit.convert(sw.nanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Returns the elapsed time in nanoseconds.
     *
     * @return the elapsed time in nanoseconds
     */
    public long nanos() {
        // Compute the elapsed time.
        // Note that nanoTime may returns negative values if the origin time is in the future.
        return Math.abs(System.nanoTime() - startTimeNanos);
    }

    /**
     * Returns the elapsed time in milliseconds.
     *
     * @return the elapsed time in milliseconds
     */
    public long millis() {
        return TimeUnit.NANOSECONDS.toMillis(nanos());
    }

    /**
     * Returns the elapsed time in seconds.
     *
     * @return the elapsed time in second
     */
    public long seconds() {
        return TimeUnit.NANOSECONDS.toSeconds(nanos());
    }
}
