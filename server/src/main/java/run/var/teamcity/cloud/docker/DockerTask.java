package run.var.teamcity.cloud.docker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * An asynchronous task to be submitted to the {@link DockerTaskScheduler}.
 *
 * <p>A task will be <em>repeatable</em> when using
 * {@link #DockerTask(String, DockerCloudErrorHandler, long, long, TimeUnit) the constructor providing delays}.
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
    private final long initialDelay;
    private final long delay;
    private final TimeUnit timeUnit;
    private final boolean repeatable;

    /**
     * Creates a one-shot task.
     *
     * @param operationName the operation name
     * @param errorHandler the error handler
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    DockerTask(String operationName, DockerCloudErrorHandler errorHandler) {
        this(operationName, errorHandler, -1, -1, null, false);
    }

    /**
     * Creates a repeatable task.
     *
     * @param operationName the operation name
     * @param errorHandler the error handler
     * @param initialDelay the delay preceding the initial scheduling of the task
     * @param delay the delay preceding the subsequent scheduling of the task
     * @param timeUnit the time unit for the delays
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if a delay is negative
     */
    DockerTask(@NotNull String operationName, @NotNull DockerCloudErrorHandler errorHandler, long initialDelay, long delay, @NotNull TimeUnit timeUnit) {
        this(operationName, errorHandler, initialDelay, delay, timeUnit, true);
    }

    private DockerTask(@NotNull String operationName, @NotNull DockerCloudErrorHandler errorHandler, long initialDelay, long delay, TimeUnit timeUnit, boolean repeatable) {
        DockerCloudUtils.requireNonNull(operationName, "Operation name cannot be null.");
        DockerCloudUtils.requireNonNull(operationName, "Error handler cannot be null.");
        if (repeatable) {
            DockerCloudUtils.requireNonNull(timeUnit, "Time unit cannot be null.");
            if (initialDelay < 0) {
                throw new IllegalArgumentException("Initial delay must be a positive integer.");
            }
            if (delay < 0) {
                throw new IllegalArgumentException("Delay must be a positive integer.");
            }
        }

        this.operationName = operationName;
        this.errorProvider = errorHandler;
        this.initialDelay = initialDelay;
        this.delay = delay;
        this.timeUnit = timeUnit;
        this.repeatable = repeatable;
    }

    /**
     * Returns {@code true} if this task is repeatable.
     *
     * @return {@code true} if this task is repeatable
     */
    boolean isRepeatable() {
        return repeatable;
    }

    /**
     * Returns the initial scheduling delay for the task. Will return -1 if this task is not repeatable.
     *
     * @return the initial scheduling delay if available, or -1
     */
    long getInitialDelay() {
        return initialDelay;
    }

    /**
     * Returns the subsequent scheduling delay for the task.  Will return -1 if this task is not repeatable.
     *
     * @return the subsequent scheduling delay if available, or -1
     */
    long getDelay() {
        return delay;
    }

    /**
     * Returns the time unit for the delay. Will be {@code null} if this task is not repeatable.
     *
     * @return the time unit for the delays or {@code null}
     *
     * @see #isRepeatable()
     */
    @Nullable
    TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Gets the operation name.
     *
     * @return the operation name.
     */
    @NotNull
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
    @NotNull
    DockerCloudErrorHandler getErrorProvider() {
        return errorProvider;
    }

    /**
     * Delegates the task execution to {@link #callInternal()}.
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
