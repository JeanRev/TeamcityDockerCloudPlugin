package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.util.concurrent.RunnableScheduledFuture;

/**
 * {@link WrappedRunnableScheduledFuture} referencing a {@link Runnable} task.
 *
 * @param <R> the type of the runnable task
 */
public class ScheduledFutureWithRunnable<R extends Runnable> extends WrappedRunnableScheduledFuture<R, Void> {
    /**
     * Creates a new wrapper instance.
     *
     * @param task    the task to be referenced
     * @param wrapped the future to be wrapped
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public ScheduledFutureWithRunnable(@Nonnull R task, @Nonnull RunnableScheduledFuture<Void> wrapped) {
        super(task, wrapped);
    }
}
