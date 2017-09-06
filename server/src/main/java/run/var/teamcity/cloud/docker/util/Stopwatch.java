package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.time.Duration;

/**
 * A very simple stopwatch implementation based on {@code System.nanoTime()}.
 * <p>
 *     Instances of this class are immutable.
 * </p>
 */
public class Stopwatch {

    private final long startTime;

    private Stopwatch(long startTime) {
        this.startTime = startTime;
    }

    /**
     * Creates a new stopwatch instance and start counting time.
     *
     * @return the new stopwatch instance
     */
    public static Stopwatch start() {
        return new Stopwatch(System.nanoTime());
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

    /**
     * Gets the elapsed duration since this timer has been started.
     *
     * @return the elapsed duration
     */
    @Nonnull
    public Duration getDuration() {
        long now = System.nanoTime();
        return Duration.ofNanos(Math.max(0, now - startTime));
    }
}
