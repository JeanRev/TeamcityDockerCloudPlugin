package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * An asynchronous task to be submitted to the {@link DockerTaskScheduler}.
 *
 * <p>A task will be <em>repeatable</em> when using
 * {@link #DockerTask(String, DockerCloudErrorHandler, Duration, Duration)} the constructor providing delays}.
 * A repeatable task will be repeatedly scheduled for execution after the expiration of the specified delay with no
 * further guarantee: depending on the current thread pool usage the scheduling, respectively the effective
 * execution of a task, may be further delayed.</p>
 *
 * <p>Each task has an associated operation name. The operation name is used to provide meaningful error messages to
 * be reported to the corresponding {@link DockerCloudErrorHandler error handler}. It will be used at the start of the
 * error message, its first letter should therefore be capitalized.</p>
 */
abstract class DockerTask implements Callable<Void> {

    private final String operationName;
    private final DockerCloudErrorHandler errorProvider;
    private final Duration initialDelay;
    private final Duration delay;

    /**
     * Creates a one-shot task.
     *
     * @param operationName the operation name
     * @param errorHandler  the error handler
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    DockerTask(String operationName, DockerCloudErrorHandler errorHandler) {
        this(operationName, errorHandler, Duration.ofMillis(-1), Duration.ofMillis(-1));
    }

    /**
     * Creates a repeatable task.
     *
     * @param operationName the operation name
     * @param errorHandler  the error handler
     * @param initialDelay  the delay preceding the initial scheduling of the task
     * @param delay         the delay preceding the subsequent scheduling of the task
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if a delay is negative
     */
    DockerTask(@Nonnull String operationName, @Nonnull DockerCloudErrorHandler errorHandler, @Nonnull Duration
            initialDelay, @Nonnull Duration delay) {
        DockerCloudUtils.requireNonNull(operationName, "Operation name cannot be null.");
        DockerCloudUtils.requireNonNull(operationName, "Error handler cannot be null.");
        DockerCloudUtils.requireNonNull(initialDelay, "Initial delay cannot be null.");
        DockerCloudUtils.requireNonNull(delay, "Delay cannot be null.");

        this.operationName = operationName;
        this.errorProvider = errorHandler;
        this.initialDelay = initialDelay;
        this.delay = delay;
    }

    /**
     * Returns the initial scheduling delay for the task.
     *
     * @return the initial scheduling delay
     */
    @Nonnull
    Duration getInitialDelay() {
        return initialDelay;
    }

    /**
     * Returns the subsequent scheduling delay for the task.  Will return a negative duration of if this task is not
     * repeatable.
     *
     * @return the subsequent scheduling delay if available
     */
    @Nonnull
    Duration getRescheduleDelay() {
        return delay;
    }

    /**
     * Gets the operation name.
     *
     * @return the operation name.
     */
    @Nonnull
    String getOperationName() {
        return operationName;
    }

    /**
     * Execute the task.
     */
    abstract void callInternal() throws Exception;

    /**
     * Gets the error provider associated with this task. The error provider will be notified if an exception occurs
     * during the asynchronous processing of a task.
     *
     * @return the error provider
     */
    @Nonnull
    DockerCloudErrorHandler getErrorProvider() {
        return errorProvider;
    }

    /**
     * Delegates the task execution to {@link #callInternal()}.
     *
     * @return {@code null}, always
     *
     * @throws Exception if any
     */
    @Override
    public final Void call() throws Exception {
        callInternal();
        return null;
    }
}
