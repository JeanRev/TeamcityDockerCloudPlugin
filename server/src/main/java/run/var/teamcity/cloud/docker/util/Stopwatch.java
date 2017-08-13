package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;

/**
 * A simple stopwatch implementation based on {@code java.time}.
 * <p>
 *     This implementation is thread safe.
 * </p>
 */
public class Stopwatch {

    private volatile Instant startTime;

    private Stopwatch() {
        reset();
    }

    /**
     * Reset this stopwatch. Bring this stopwatch back to 0, and start counting immediately.
     */
    public void reset() {
        startTime = Instant.now();
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
    public static Duration measure(@Nonnull Runnable runnable) {
        DockerCloudUtils.requireNonNull(runnable, "Runnable cannot be null.");
        Stopwatch sw = start();
        runnable.run();
        return sw.getDuration();
    }

    public Duration getDuration() {
        return Duration.between(startTime, Instant.now());
    }
}
