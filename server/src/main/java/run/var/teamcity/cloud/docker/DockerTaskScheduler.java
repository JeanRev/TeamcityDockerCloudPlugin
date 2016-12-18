package run.var.teamcity.cloud.docker;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.InstanceStatus;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;
import run.var.teamcity.cloud.docker.util.WrappedRunnableScheduledFuture;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.*;
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
     * Our {@link ExecutorService}. We use a "<i>scheduled</i>" thread pool to handle repeatable tasks.
     */
    private final ScheduledThreadPoolExecutor executor;

    /**
     * Creates a new scheduler instance.
     *
     * @param threadPoolSize    the size of the thread pool
     * @param usingDaemonThread {@code true} to use daemon threads
     *
     * @throws IllegalArgumentException if {@code connectionPoolSize} is smaller than 1
     */
    DockerTaskScheduler(int threadPoolSize, boolean usingDaemonThread) {
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Thread pool size must be strictly greater than 1.");
        }
        executor = new ScheduledThreadPoolExecutor(threadPoolSize,
                new NamedThreadFactory("DockerTaskScheduler", usingDaemonThread)) {
            @Override
            protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
                return new WrappedRunnableScheduledFuture<>(callable, task);
            }

            @Override
            protected void afterExecute(Runnable runnable, Throwable throwable) {

                assert runnable instanceof WrappedRunnableScheduledFuture;

                WrappedRunnableScheduledFuture<?, ?> wrappedRunnable = (WrappedRunnableScheduledFuture<?, ?>) runnable;
                if (throwable == null) {
                    try {
                        wrappedRunnable.get();
                    } catch (Exception e) {
                        throwable = e;
                    }
                }

                Object task = wrappedRunnable.getTask();

                if (task instanceof ScheduleRepetableTask) {
                    return;
                }

                assert task instanceof DockerTask;

                try {
                    lock.lock();
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

                    if (throwable == null) {
                        LOG.debug("Task " + dockerTask + " completed without error.");
                    } else {
                        LOG.error("Task " + dockerTask + " execution failed.", throwable);
                        dockerTask.getErrorProvider().notifyFailure(dockerTask.getOperationName(), throwable);

                    }

                    if (dockerTask.isRepeatable()) {
                        // Repeatable tasks are always rescheduled, even if their last execution failed. This permits
                        // the client to recover from an error status.
                        LOG.debug("Rescheduling task: " + dockerTask);
                        executor.schedule(new ScheduleRepetableTask(dockerTask), dockerTask.getDelay(), dockerTask.getTimeUnit());
                    }

                    scheduleNextTasks();
                } finally {
                    lock.unlock();
                }
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
                executor.schedule(new ScheduleRepetableTask(task), initialDelay, task.getTimeUnit());
                return;
            }
        }

        submitTask(task);
    }

    private void submitTask(DockerTask task) {
        assert task != null;
        try {
            lock.lock();

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
                    LOG.info("Submitting client task " + clientTask + " for execution.");
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
     * Shutdown the scheduler. All already scheduled tasks will eventually submitted for execution, but no new
     * scheduling will be accepted.
     */
    void shutdown() {
        try {
            lock.lock();
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
        }
    }
}
