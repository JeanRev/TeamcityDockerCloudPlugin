package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.WebLinks;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.util.WrappedRunnableScheduledFuture;

import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


class DefaultContainerTestManager extends ContainerTestManager {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestManager.class);

    private final static long REFRESH_TASK_RATE_SEC = 10;
    final static long CLEANUP_DEFAULT_TASK_RATE_SEC = 60;
    final static long TEST_DEFAULT_IDLE_TIME_SEC = TimeUnit.MINUTES.toSeconds(10);

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, ContainerSpecTest> tasks = new HashMap<>();
    private final DockerImageNameResolver imageNameResolver;
    private final DockerClientFactory dockerClientFactory;
    private final long testMaxIdleTimeSec;
    private final long cleanupRateSec;
    private final BuildAgentManager agentMgr;
    private final WebLinks webLinks;

    private ScheduledExecutorService executorService = null;
    private boolean disposed = false;

    DefaultContainerTestManager(DockerImageNameResolver imageNameResolver,
                         DockerClientFactory dockerClientFactory, BuildAgentManager agentMgr, WebLinks webLinks) {
        this(imageNameResolver, dockerClientFactory, agentMgr, webLinks, TEST_DEFAULT_IDLE_TIME_SEC,
                CLEANUP_DEFAULT_TASK_RATE_SEC);
    }


    DefaultContainerTestManager(DockerImageNameResolver imageNameResolver,
                                DockerClientFactory dockerClientFactory, BuildAgentManager agentMgr, WebLinks webLinks,
                                long testMaxIdleTimeSec, long cleanupRateSec) {
        this.imageNameResolver = imageNameResolver;
        this.dockerClientFactory = dockerClientFactory;
        this.testMaxIdleTimeSec = testMaxIdleTimeSec;
        this.cleanupRateSec = cleanupRateSec;
        this.agentMgr = agentMgr;
        this.webLinks = webLinks;
    }

    TestContainerStatusMsg doAction(Action action, UUID testUuid, DockerCloudClientConfig clientConfig,
                                    DockerImageConfig imageConfig) {
        if (executorService == null) {
            activate();
        }

        if (action == null) {
            throw new ActionException(HttpServletResponse.SC_BAD_REQUEST, "Missing action parameter");
        }

        ContainerSpecTest test = retrieveTestTask(testUuid);

        if (action == Action.CREATE) {
            test = submitCreateJob(clientConfig, imageConfig);
        } else {
            if (test == null) {
                throw new ActionException(HttpServletResponse.SC_NOT_FOUND, "Bad or expired request.");
            }
            switch (action) {
                case START:
                    submitStartTask(test);
                    break;
                case DISPOSE:
                    disposeTask(test);
                    break;
                case CANCEL:
                    dispose(test);
                    break;
                case QUERY:
                    // Nothing to do.
                    break;
                default:
                    throw new AssertionError("Unknown enum member: " + action);
            }
        }

        assert test != null;

        test.notifyInteraction();

        return test.getStatusMsg();
    }

    private ContainerSpecTest retrieveTestTask(UUID testUuid) {

        ContainerSpecTest test = null;
        if (testUuid != null) {
            try {
                lock.lock();
                test = tasks.get(testUuid);
            } finally {
                lock.unlock();
            }
        }

        return  test;
    }

    private void activate() {
        if (executorService == null) {
            lock.lock();
            try {
                executorService = createScheduledExecutor();
                executorService.scheduleWithFixedDelay(new CleanupTask(), cleanupRateSec, cleanupRateSec,
                        TimeUnit.SECONDS);
            } finally {
                lock.unlock();
            }
        }
    }

    private void passivate() {
        if (executorService != null) {
            lock.lock();
            try {
                executorService.shutdownNow();
                executorService = null;
            } finally {
                lock.unlock();
            }
        }
    }

    private ContainerSpecTest submitCreateJob(DockerCloudClientConfig clientConfig, DockerImageConfig imageConfig) {

        ContainerSpecTest test = newTestInstance(clientConfig);

        URL serverURL = clientConfig.getServerURL();
        String serverURLStr = serverURL != null ? serverURL.toString() : webLinks.getRootUrl();

        CreateContainerTestTask testTask = new CreateContainerTestTask(test, imageConfig, serverURLStr, test
                .getUuid(), imageNameResolver);
        test.setCurrentTask(schedule(testTask));

        return test;
    }


    private void submitStartTask(ContainerSpecTest test) {

        String containerId = test.getContainerId();
        if (containerId == null) {
            LOG.error("Cannot dispose container for test " + test.getUuid() + ", no container ID available.");
            throw new ActionException(HttpServletResponse.SC_BAD_REQUEST, "Container not registered.");
        }
        StartContainerTestTask testTask = new StartContainerTestTask(test, containerId, test.getUuid());
        test.setCurrentTask(schedule(testTask));
    }

    private void disposeTask(ContainerSpecTest test) {

        lock.lock();

        try {
            String containerId = test.getContainerId();

            if (containerId == null) {
                LOG.error("Cannot dispose container for test " + test.getUuid() + ", no container ID available.");
                throw new ActionException(HttpServletResponse.SC_BAD_REQUEST, "Container not registered.");
            }

            DisposeContainerTestTask disposeTask = new DisposeContainerTestTask(test, containerId);

            test.setCurrentTask(schedule(disposeTask));
        } finally {
            lock.unlock();
        }
    }

    private void dispose(ContainerSpecTest test) {

        LOG.info("Disposing test task: " + test.getUuid());

        DockerClient client;
        ContainerTestStatusListener statusListener;
        String containerId;

        try {
            lock.lock();

            tasks.remove(test.getUuid());

            if (tasks.isEmpty()) {
                passivate();
            }

            cancelFutureQuietly(test.getCurrentTaskFuture());
            client = test.getDockerClient();
            statusListener = test.getStatusListener();
            containerId = test.getContainerId();
        } finally {
            lock.unlock();
        }

        // Dispose all IO-bound resources without locking.
        if (statusListener != null) {
            statusListener.disposed();
        }

        if (containerId != null) {
            try {
                try {
                    client.stopContainer(containerId, 10);
                } catch (ContainerAlreadyStoppedException e)  {
                    // Ignore.
                }
                try {
                    client.removeContainer(containerId, true, true);
                } catch (NotFoundException e) {
                    // Ignore
                }
            } catch (DockerClientException e) {
                // Ignore;
            } catch (Exception e) {
                LOG.error("Unexpected error while disposing test instance: " + test.getUuid(), e);
            }
        }
        client.close();
    }

    private void cancelFutureQuietly(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private ContainerSpecTest newTestInstance(DockerCloudClientConfig clientConfig) {
        try {
            lock.lock();

            ContainerSpecTest test = ContainerSpecTest.newTestInstance(clientConfig, dockerClientFactory, agentMgr);

            boolean duplicate = tasks.put(test.getUuid(), test) != null;
            assert !duplicate;

            return test;
        } finally {
            lock.unlock();
        }
    }

    private ScheduledExecutorService createScheduledExecutor() {
        return new ScheduledThreadPoolExecutor(2, new NamedThreadFactory("ContainerTestWorker")) {
            @SuppressWarnings("unchecked")
            @Override
            protected RunnableScheduledFuture decorateTask(Runnable runnable, RunnableScheduledFuture
                    task) {
                return new ScheduledFutureWithRunnable<>(runnable, task);
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                assert r instanceof WrappedRunnableScheduledFuture;

                @SuppressWarnings("unchecked")
                WrappedRunnableScheduledFuture<Runnable, ?> future = (WrappedRunnableScheduledFuture<Runnable, ?>) r;
                Runnable task = future.getTask();

                if (task instanceof ContainerTestTask) {

                    ContainerTestTask containerTestTask = (ContainerTestTask) task;
                    ContainerSpecTest test = (ContainerSpecTest) containerTestTask.getTestTaskHandler();

                    if (t == null && future.isDone()) {
                        try {
                            future.get();
                        } catch (Exception e) {
                            t = e;
                        }
                    }
                    if (t == null) {
                        if (containerTestTask.getStatus() == TestContainerStatusMsg.Status.PENDING) {
                            schedule(task, REFRESH_TASK_RATE_SEC, TimeUnit.SECONDS);
                        }
                    } else if (t instanceof InterruptedException || t instanceof CancellationException) {
                        // Cancelled task, ignore.
                    } else {
                        // We should never end here into normal circumstances: the test tasks base class should handle
                        // itself checked and unchecked exceptions and update its internal state accordingly.
                        // In such case we just discard the test instance.
                        LOG.error("Unexpected task failure for test: " + test, t);
                        dispose(test);
                    }
                } else {
                    assert task instanceof CleanupTask;
                }
            }
        };
    }

    private <T extends ContainerTestTask> ScheduledFutureWithRunnable<T> schedule(T task) {

        try {
            lock.lock();

            if (executorService == null) {
                activate();
            }

            @SuppressWarnings("unchecked")
            ScheduledFutureWithRunnable<T> futureTask = (ScheduledFutureWithRunnable<T>) executorService
                    .submit(task);
            return futureTask;

        } finally {
            lock.unlock();
        }
    }

    private class CleanupTask implements Runnable {

        @Override
        public void run() {

            List<ContainerSpecTest> tests = new ArrayList<>();

            try {
                lock.lock();

                for (ContainerSpecTest test : tasks.values()) {
                    if (test.getCurrentTaskFuture() != null) {
                        if (Math.abs(System.nanoTime() - test.getLastInteraction()) > TimeUnit.SECONDS.toNanos
                                (testMaxIdleTimeSec)) {
                            tests.add(test);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }

            for (ContainerSpecTest test : tests) {
                dispose(test);
            }
        }
    }

    @Override
    void setStatusListener(UUID testUuid, ContainerTestStatusListener listener) {
        lock.lock();
        try {
            ContainerSpecTest test = tasks.get(testUuid);
            if (test == null) {
                throw new IllegalArgumentException("Unknown test UUID: " + testUuid);
            }

            test.setStatusListener(listener);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void dispose() {
        try {
            lock.lock();

            if (disposed) {
                return;
            }

            for (ContainerSpecTest test : new ArrayList<>(tasks.values())) {
                dispose(test);
            }

            passivate();

            disposed = true;
        } finally {
            lock.unlock();
        }
    }
}
