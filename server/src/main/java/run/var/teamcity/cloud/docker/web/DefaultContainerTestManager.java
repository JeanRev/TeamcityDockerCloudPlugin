package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.*;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.client.*;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.util.WrappedRunnableScheduledFuture;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;


class DefaultContainerTestManager extends ContainerTestManager {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestManager.class);

    private final static long REFRESH_TASK_RATE_SEC = 10;
    final static long CLEANUP_DEFAULT_TASK_RATE_SEC = 10;
    final static long TEST_DEFAULT_IDLE_TIME_SEC = TimeUnit.MINUTES.toSeconds(10);

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, ContainerSpecTest> tests = new HashMap<>();
    private final DockerImageNameResolver imageNameResolver;
    private final DockerClientFactory dockerClientFactory;
    private final long testMaxIdleTimeSec;
    private final long cleanupRateSec;
    private final SBuildServer buildServer;
    private final WebLinks webLinks;
    private final StreamingController streamingController;
    private final Set<UUID> agentToRemove = new HashSet<>();

    private ScheduledExecutorService executorService = null;
    private boolean disposed = false;

    DefaultContainerTestManager(DockerImageNameResolver imageNameResolver,
                         DockerClientFactory dockerClientFactory, SBuildServer buildServer, WebLinks webLinks,
                                StreamingController streamingController) {
        this(imageNameResolver, dockerClientFactory, buildServer, webLinks, TEST_DEFAULT_IDLE_TIME_SEC,
                CLEANUP_DEFAULT_TASK_RATE_SEC, streamingController);
    }


    DefaultContainerTestManager(DockerImageNameResolver imageNameResolver,
                                DockerClientFactory dockerClientFactory, SBuildServer buildServer, WebLinks webLinks,
                                long testMaxIdleTimeSec, long cleanupRateSec, StreamingController streamingController) {
        this.imageNameResolver = imageNameResolver;
        this.dockerClientFactory = dockerClientFactory;
        this.testMaxIdleTimeSec = testMaxIdleTimeSec;
        this.cleanupRateSec = cleanupRateSec;
        this.buildServer = buildServer;
        this.webLinks = webLinks;
        this.streamingController = streamingController;

        // TODO: remove on dispose
        buildServer.addListener(new ServerListener());

        cleanUpTestAgents();
    }

    @Override
    @NotNull
    UUID createNewTestContainer(@NotNull DockerCloudClientConfig clientConfig, @NotNull DockerImageConfig imageConfig,
                                @NotNull ContainerTestListener listener) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client configuration cannot be null.");
        DockerCloudUtils.requireNonNull(imageConfig, "Image configuration cannot be null.");
        DockerCloudUtils.requireNonNull(listener, "Test listener cannot be null.");

        ContainerSpecTest test = newTestInstance(clientConfig, listener);

        URL serverURL = clientConfig.getServerURL();
        String serverURLStr = serverURL != null ? serverURL.toString() : webLinks.getRootUrl();

        CreateContainerTestTask testTask = new CreateContainerTestTask(test, imageConfig, serverURLStr, test
                .getUuid(), imageNameResolver);
        test.setCurrentTask(schedule(testTask));

        return test.getUuid();
    }

    @Override
    void startTestContainer(@NotNull UUID testUuid) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");

        ContainerSpecTest test = retrieveTestInstance(testUuid);

        String containerId = test.getContainerId();

        if (containerId == null) {
            throw new ActionException(HttpServletResponse.SC_BAD_REQUEST, "Container not created.");
        }

        assert containerId != null;

        StartContainerTestTask testTask = new StartContainerTestTask(test, containerId, test.getUuid());
        test.setCurrentTask(schedule(testTask));
    }

    private static final Pattern VT100_ESCAPE_PTN = Pattern.compile("\u001B\\[[\\d;]*[^\\d;]");

    @Override
    public String getLogs(@NotNull UUID testUuid) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");

        ContainerSpecTest test = retrieveTestInstance(testUuid);

        String containerId = test.getContainerId();

        if (containerId == null) {
            throw new ActionException(HttpServletResponse.SC_BAD_REQUEST, "Container not created.");
        }

        StringBuilder sb = new StringBuilder(5 * 1024);

        try (StreamHandler handler = ((DefaultDockerClient) test.getDockerClient()).
                streamLogs(containerId, 10000, StdioType.all(), false)) {
            StdioInputStream streamFragment;
            while ((streamFragment = handler.getNextStreamFragment()) != null) {
                sb.append(DockerCloudUtils.readUTF8String(streamFragment));
            }
        } catch (IOException e) {
            throw new ActionException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch logs: " + e.getMessage());
        }

        return VT100_ESCAPE_PTN.matcher(sb).replaceAll("");
    }

    @Override
    void dispose(@NotNull UUID testUuid) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");

        ContainerSpecTest test = retrieveTestInstance(testUuid);

        dispose(test);
    }

    @Override
    void notifyInteraction(@NotNull UUID testUUid) {
        ContainerSpecTest test = retrieveTestInstance(testUUid);
        test.notifyInteraction();
    }

    private ContainerSpecTest retrieveTestInstance(UUID testUuid) {

        ContainerSpecTest test = null;
        if (testUuid != null) {
            try {
                lock.lock();
                test = tests.get(testUuid);
            } finally {
                lock.unlock();
            }
        }

        if (test == null) {
            throw new ActionException(HttpServletResponse.SC_BAD_REQUEST, "Bad or expired token: " + testUuid);
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

    private void dispose(ContainerSpecTest test) {

        LOG.info("Disposing test task: " + test.getUuid());

        DockerClient client;
        ContainerTestListener statusListener;
        String containerId;

        try {
            lock.lock();

            tests.remove(test.getUuid());

            if (tests.isEmpty() && agentToRemove.isEmpty()) {
                passivate();
            }

            if (streamingController != null) {
                streamingController.clearConfiguration(test.getUuid());
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

        cleanUpTestAgents();
    }

    private void cancelFutureQuietly(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private ContainerSpecTest newTestInstance(DockerCloudClientConfig clientConfig,
                                              ContainerTestListener listener) {
        try {
            lock.lock();

            ContainerSpecTest test = ContainerSpecTest.newTestInstance(clientConfig, dockerClientFactory, listener,
                    streamingController);

            boolean duplicate = tests.put(test.getUuid(), test) != null;
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
                        LOG.info(test.getUuid()  + " was interrupted.", t);
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

                for (ContainerSpecTest test : DefaultContainerTestManager.this.tests.values()) {
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

            cleanUpTestAgents();
        }
    }

    private void cleanUpTestAgents() {

        BuildAgentManager agentMgr = buildServer.getBuildAgentManager();
        if (!(agentMgr instanceof  BuildAgentManagerEx)) {
            // We currently end here using our mocked build agent manager used for testing.
            // BuildAgentManagerEx rely on unpublished APIs, and we cannot provide an implementation for it easily.
            return;
        }
        for (SBuildAgent agent : ((BuildAgentManagerEx) agentMgr).getAllAgents(true)) {
            String uuidStr = DockerCloudUtils.getEnvParameter(agent, DockerCloudUtils.ENV_TEST_INSTANCE_ID);
            UUID instanceUuid = DockerCloudUtils.tryParseAsUUID(uuidStr);
            if (instanceUuid != null) {

                boolean removeAgent = false;
                lock.lock();
                try {
                    if (!tests.containsKey(instanceUuid)) {
                        if (agent.isRegistered()) {
                            agentToRemove.add(instanceUuid);
                            activate();
                        } else {
                            removeAgent = true;
                            agentToRemove.remove(instanceUuid);
                        }
                    }
                } finally {
                    lock.unlock();
                }

                if (removeAgent) {
                    try {
                        agentMgr.removeAgent(agent, null);
                    } catch (AgentCannotBeRemovedException e) {
                        LOG.warn("Cannot remove agent: " + agent, e);
                    }
                }
            }
        }

        lock.lock();
        try {
            if (tests.isEmpty() && agentToRemove.isEmpty()) {
                passivate();
            }
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

            for (ContainerSpecTest test : new ArrayList<>(tests.values())) {
                dispose(test);
            }

            passivate();

            disposed = true;
        } finally {
            lock.unlock();
        }
    }

    private class ServerListener extends BuildServerAdapter {

        @Override
        public void agentRegistered(@NotNull SBuildAgent agent, long currentlyRunningBuildId) {
            // We attempt here to disable the agent as soon as possible to prevent it from starting any job.
            UUID testInstanceUuid = DockerCloudUtils.tryParseAsUUID(DockerCloudUtils.getEnvParameter(agent,
                    DockerCloudUtils.ENV_TEST_INSTANCE_ID));

            if (testInstanceUuid != null) {
                agent.setEnabled(false, null, "Docker cloud test instance.");
                lock.lock();
                try {
                    agentToRemove.add(testInstanceUuid);
                    activate();
                    ContainerSpecTest test = tests.get(testInstanceUuid);
                    if (test != null) {
                        test.setBuildAgentDetected(true);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
