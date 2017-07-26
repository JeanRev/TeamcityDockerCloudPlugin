package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link RunnableScheduledFuture} wrapper with a reference to a source task.
 *
 * <p>This class permits to keep tracks of the callable associated with a {@code RunnableScheduledFuture} to which all
 * method invocations will be delegated.</p>
 *
 * @param <V> the callable return type
 */
public class WrappedRunnableScheduledFuture<T, V> implements RunnableScheduledFuture<V> {

    private final T task;
    private final RunnableScheduledFuture<V> wrapped;

    /**
     * s
     * Creates a new wrapper instance.
     *
     * @param task the source task
     * @param wrapped the object to which all invocations will be delegated
     */
    public WrappedRunnableScheduledFuture(@Nonnull T task, @Nonnull RunnableScheduledFuture<V> wrapped) {
        this.task = DockerCloudUtils.requireNonNull(task, "Source task cannot be null.");
        this.wrapped = DockerCloudUtils.requireNonNull(wrapped, "Wrapped runnable cannot be null.");
    }

    @Nonnull
    public T getTask() {
        return task;
    }

    @Override
    public boolean isPeriodic() {
        return wrapped.isPeriodic();
    }

    @Override
    public long getDelay(@Nonnull TimeUnit unit) {
        return wrapped.getDelay(unit);
    }

    @Override
    public int compareTo(@Nonnull Delayed o) {
        return wrapped.compareTo(o);
    }

    @Override
    public void run() {
        wrapped.run();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return wrapped.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return wrapped.isCancelled();
    }

    @Override
    public boolean isDone() {
        return wrapped.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return wrapped.get();
    }

    @Override
    public V get(long timeout, @Nonnull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return wrapped.get(timeout, unit);
    }
}
