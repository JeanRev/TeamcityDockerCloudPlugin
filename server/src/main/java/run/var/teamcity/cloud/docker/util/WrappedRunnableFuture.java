package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link RunnableFuture} wrapper with a reference to a source task.
 *
 * <p>This class permits to keep tracks of the callable associated with a {@code RunnableFuture} to which all method
 * invocations will be delegated.</p>
 *
 * @param <V> the callable return type
 */
public class WrappedRunnableFuture<T, V> implements RunnableFuture<V> {

    private final T task;
    private final RunnableFuture<V> wrapped;

    /**
     * s
     * Creates a new wrapper instance.
     *
     * @param task the source task
     * @param wrapped the object to which all invocations will be delegated
     */
    public WrappedRunnableFuture(@Nonnull T task, @Nonnull RunnableFuture<V> wrapped) {
        DockerCloudUtils.requireNonNull(task, "Source task cannot be null.");
        DockerCloudUtils.requireNonNull(wrapped, "Wrapped runnable cannot be null.");
        this.task = task;
        this.wrapped = wrapped;
    }

    @Nonnull
    public T getTask() {
        return task;
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
