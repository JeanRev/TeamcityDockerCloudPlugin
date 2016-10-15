package run.var.teamcity.cloud.docker.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.RunnableScheduledFuture;

/**
 * Created by jr on 24.08.16.
 */
public class ScheduledFutureWithRunnable<R extends Runnable> extends WrappedRunnableScheduledFuture<R, Void> {
    public ScheduledFutureWithRunnable(@NotNull R task, @NotNull RunnableScheduledFuture<Void> wrapped) {
        super(task, wrapped);
    }
}
