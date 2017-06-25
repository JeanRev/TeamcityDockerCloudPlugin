package run.var.teamcity.cloud.docker;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.InstanceStatus;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;
import run.var.teamcity.cloud.docker.util.WrappedRunnableFuture;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Task scheduler for a {@link DefaultDockerCloudClient}. The scheduler ensure the sequential execution of tasks that may
 * otherwise lead to conflicting interactions with the Docker daemon for a given container (eg. starting and
 * destroying a container concurrently) when processing {@link DockerInstanceTask}s. In addition, the scheduler
 * ensure that {@link DockerClientTask}s, related to the general lifecycle of the cloud client, to be executed
 * fully sequentially, with no other concurrent operation.
 *
 * <p>This management is implemented using a waiting queue (in addition to the internal queue of the
 * {@link ThreadPoolExecutor}) where tasks are considered <i>scheduled</i> but not yet <i>submitted</i>. Submission
 * is only performed when all the required conditions are met:
 * <ul>
 * <li>Client tasks: require that no other task to be to be submitted for execution. They will always be
 * processed before instance tasks independently of their submission order.</li>
 * <li>Instance tasks: require that no client task, or task for the same cloud instance, to be submitted for
 * execution.</li>
 * </ul>
 * </p>
 * <p>The size of the thread pool used to process tasks is configurable as constructor parameter. In addition a
 * dedicated thread will also be used to manage the scheduler internal state.
 * </p>
 *
 * <p>Instances of this class are thread safe.</p>
 */
class DockerTaskScheduler {

    private final static Logger LOG = DockerCloudUtils.getLogger(DockerTaskScheduler.class);

    // This lock ensure a thread-safe usage of all the variables below.
    private final ReentrantLock lock = new ReentrantLock();

    private final Set<UUID> submittedInstancesUUID = new HashSet<>();
    private final LinkedList<DockerClientTask> clientTasks = new LinkedList<>();
    private final LinkedList<DockerInstanceTask> instancesTask = new LinkedList<>();
    private boolean clientTaskSubmitted = false;
    private boolean shutdownRequested = false;

    /**
     * Executor service for externally submitted tasks.
     */
    private final ExecutorService executor;
    /**
     * Executor service for maintenance tasks.
     */
    private final ScheduledExecutorService mngExecutor;

    /**
     * Creates a new scheduler instance.
     *
     * @param threadPoolSize    the size of the thread pool for processing task
     * @param usingDaemonThread {@code true} to use daemon threads
     * @param taskTimeoutMillis timeout in milliseconds after which a running task will be cancelled
     *
     * @throws IllegalArgumentException if {@code connectionPoolSize} is smaller than 1
     */
    DockerTaskScheduler(int threadPoolSize, boolean usingDaemonThread, long taskTimeoutMillis) {
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Thread pool size must be strictly greater than 1.");
        }
        if (taskTimeoutMillis < 0) {
            throw new IllegalArgumentException("Task timeout must be a positive integer.");
        }

        // Creates the single-thread scheduled executor use for managing the scheduler internal state.
        mngExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("DockerTaskSchedulerMngt"));

        // Creates the main task executor (same default settings as Executors.newFixedThreadPool()).
        executor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), new NamedThreadFactory("DockerTaskScheduler", usingDaemonThread)) {

            @Override
            protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
                // Wrap the provided callable in our own runnable-future so we may retrieve the underlying task in the
                // execution callback methods.
                return new WrappedRunnableFuture<>(callable, super.newTaskFor(callable));
            }

            @Override
            protected void beforeExecute(Thread thread, Runnable runnable) {
                assert runnable instanceof WrappedRunnableFuture;

                // Schedule a task to check for timeout.
                mngExecutor.schedule(new TimeoutHandlingTask((Future<?>) runnable), taskTimeoutMillis,
                        TimeUnit.MILLISECONDS);

                super.beforeExecute(thread, runnable);
            }

            @Override
            protected void afterExecute(Runnable runnable, Throwable throwable) {

                super.afterExecute(runnable, throwable);

                assert runnable instanceof WrappedRunnableFuture;

                WrappedRunnableFuture<?,?> wrappedRunnableFuture = (WrappedRunnableFuture<?, ?>) runnable;
                if (throwable == null) {
                    try {
                        wrappedRunnableFuture.get();
                    } catch (Exception e) {
                        throwable = e;
                    }
                }

                Object task = wrappedRunnableFuture.getTask();

                assert task instanceof DockerTask;

                lock.lock();
                try {
                    DockerTask dockerTask = (DockerTask) task;

                    LOG.debug("Task execution completed.");

                    if (dockerTask instanceof DockerClientTask) {
                        assert clientTaskSubmitted;
                        clientTaskSubmitted = false;
                    } else {
                        assert dockerTask instanceof DockerInstanceTask;
                        DockerInstanceTask instanceTask = (DockerInstanceTask) task;
                        DockerInstance instance = instanceTask.getInstance();
                        boolean unmarked = submittedInstancesUUID.remove(instance.getUuid());
                        assert unmarked : "Task " + instanceTask + " was not marked as being processed.";
                    }

                    if (wrappedRunnableFuture.isCancelled() && !shutdownRequested) {
                        // The task has been cancelled but no shutdown of the scheduler is planned. It must then have
                        // been caused by a task timeout. The corresponding error provider will need to be notified
                        // with a corresponding exception.
                        throwable = new DockerTaskSchedulerException("Operation took too long to complete.", throwable);
                    }

                    if (throwable == null) {
                        LOG.debug("Task " + dockerTask + " completed without error.");
                    } else {
                        LOG.error("Task " + dockerTask + " execution failed.", throwable);
                        dockerTask.getErrorProvider().notifyFailure(dockerTask.getOperationName() + " failed.", throwable);

                    }

                    if (!shutdownRequested && dockerTask.isRepeatable()) {
                        // Repeatable tasks are always rescheduled, even if their last execution failed. This permits
                        // the client to recover from an error status.
                        LOG.debug("Rescheduling task: " + dockerTask);
                        mngExecutor.schedule(new ScheduleRepetableTask(dockerTask), dockerTask.getDelay(), dockerTask.getTimeUnit());
                    }

                    scheduleNextTasks();
                } finally {
                    lock.unlock();
                }
            }

            @Override
            protected void terminated() {
                mngExecutor.shutdown();
            }
        };
    }

    /**
     * Schedule a client task.
     *
     * @param clientTask the task
     *
     * @throws NullPointerException if {@code clientTask} is {@code null}
     */
    void scheduleClientTask(@Nonnull DockerClientTask clientTask) {
        DockerCloudUtils.requireNonNull(clientTask, "Client task cannot be null.");
        LOG.debug("Scheduling client: " + clientTask);
        submitTaskWithInitialDelay(clientTask);
    }

    /**
     * Schedule a client task.
     *
     * @param instanceTask the task
     *
     * @throws NullPointerException if {@code instanceTask} is {@code null}
     */
    void scheduleInstanceTask(@Nonnull DockerInstanceTask instanceTask) {
        DockerCloudUtils.requireNonNull(instanceTask, "Instance task cannot be null.");
        LOG.debug("Scheduling instance task: " + clientTasks);
        submitTaskWithInitialDelay(instanceTask);
    }

    private void submitTaskWithInitialDelay(DockerTask task) {
        assert task != null;
        if (task.isRepeatable()) {
            long initialDelay = task.getInitialDelay();
            if (initialDelay > 0) {
                TimeUnit timeUnit = task.getTimeUnit();
                assert timeUnit != null;
                mngExecutor.schedule(new ScheduleRepetableTask(task), initialDelay, timeUnit);
                return;
            }
        }

        submitTask(task);
    }

    private void submitTask(DockerTask task) {
        assert task != null;
        lock.lock();
        try {
            if (shutdownRequested) {
                throw new RejectedExecutionException("Scheduler will shutdown.");
            }

            if (task instanceof DockerClientTask) {
                clientTasks.add((DockerClientTask) task);
            } else {
                assert task instanceof DockerInstanceTask;
                instancesTask.add((DockerInstanceTask) task);
            }
            scheduleNextTasks();
        } finally {
            lock.unlock();
        }
    }

    private void scheduleNextTasks() {
        assert lock.isHeldByCurrentThread();

        LOG.debug("Scheduling status: submitted instance tasks: " + submittedInstancesUUID.size() + ", client task " +
                "submitted: " + clientTaskSubmitted + ", instances tasks scheduled: " + instancesTask.size() + ", " +
                " client tasks scheduled: " + clientTasks.size());

        if (!clientTaskSubmitted) {
            if (!clientTasks.isEmpty()) {
                // Some client tasks are waiting, we will submit them as soon as possible, but not before all instances
                // tasks are processed.
                if (submittedInstancesUUID.isEmpty()) {
                    DockerClientTask clientTask = clientTasks.pollFirst();
                    LOG.debug("Submitting client task " + clientTask + " for execution.");
                    executor.submit(clientTask);
                    // Mark the client task as being submitted.
                    clientTaskSubmitted = true;
                }
            } else {
                // No client tasks are waiting or is being processed, we may execute instance tasks.
                Iterator<DockerInstanceTask> itr = instancesTask.iterator();
                while (itr.hasNext()) {
                    DockerInstanceTask instanceTask = itr.next();
                    DockerInstance instance = instanceTask.getInstance();
                    UUID instanceUuid = instance.getUuid();
                    // Only submit one task for a given instance at a time.
                    if (!submittedInstancesUUID.contains(instanceUuid)) {
                        LOG.debug("Submitting instance task " + instanceTask + " for execution.");
                        InstanceStatus scheduledStatus = instanceTask.getScheduledStatus();
                        if (scheduledStatus != null) {
                            instance.setStatus(scheduledStatus);
                        }

                        // Mark the instance tasks as being submitted.
                        submittedInstancesUUID.add(instanceUuid);
                        itr.remove();
                        executor.submit(instanceTask);
                    } else {
                        LOG.debug("Tasks for instance " + instance.getUuid() + " already submitted, delaying scheduled"
                                + " task");
                    }
                }
            }
        } else {
            LOG.debug("Client task submitted for execution, skipping submitting other tasks.");
        }

        shutdownCheck();
    }

    private class ScheduleRepetableTask implements Callable<Void> {
        final DockerTask task;

        ScheduleRepetableTask(DockerTask task) {
            assert task != null && task.isRepeatable();
            this.task = task;
        }

        @Override
        public Void call() throws Exception {
            submitTask(task);
            return null;
        }
    }

    /**
     * Management task to cancel a future instance if it has not completed yet. This task is expected to be scheduled
     * for execution at the time the timeout for the provided future is expiring.
     */
    private class TimeoutHandlingTask implements Callable<Void> {

        final Future<?> future;

        TimeoutHandlingTask(Future<?> future) {
            assert future != null;
            this.future = future;
        }

        @Override
        public Void call() throws Exception {
            if (!future.isDone()) {
                LOG.warn("Timeout reached, interrupting task.");
                future.cancel(true);
            }
            return null;
        }
    }

    /**
     * Shutdown the scheduler. All already scheduled tasks will eventually submitted for execution, but no new
     * scheduling will be accepted.
     */
    void shutdown() {
        lock.lock();
        try {
            shutdownRequested = true;
            shutdownCheck();
        } finally {
            lock.unlock();
        }
    }

    private void shutdownCheck() {
        assert lock.isHeldByCurrentThread();

        if (shutdownRequested && instancesTask.isEmpty() && clientTasks.isEmpty()) {
            executor.shutdown();
            // Note: mngExecutor will be shutdown when once the main executor has been terminated.
        }
    }
}
