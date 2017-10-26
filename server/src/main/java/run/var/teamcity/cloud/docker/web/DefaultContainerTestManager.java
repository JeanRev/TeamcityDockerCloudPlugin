package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.AgentCannotBeRemovedException;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.BuildAgentManagerEx;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.WebLinks;
import org.springframework.beans.factory.annotation.Autowired;
import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerRegistryClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.util.WrappedRunnableScheduledFuture;

import javax.annotation.Nonnull;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Default {@link ContainerTestManager} implementation.
 */
public class DefaultContainerTestManager implements ContainerTestManager {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestManager.class);

    private final static Duration REFRESH_TASK_RATE = Duration.ofSeconds(10);
    final static Duration CLEANUP_DEFAULT_TASK_RATE = Duration.ofSeconds(10);
    final static Duration TEST_DEFAULT_IDLE_TIME = Duration.ofMinutes(10);

    private final LockHandler lock = LockHandler.newReentrantLock();
    private final Map<UUID, DefaultAgentHolderTestHandler> tests = new HashMap<>();
    private final DockerImageNameResolver imageNameResolver;
    private final Duration testMaxIdleTime;
    private final Duration cleanupRate;
    private final SBuildServer buildServer;
    private final WebLinks webLinks;
    private final Set<UUID> agentToRemove = new HashSet<>();

    private ScheduledExecutorService executorService = null;
    private boolean disposed = false;

    @Autowired
    DefaultContainerTestManager(SBuildServer buildServer, WebLinks webLinks) {
        this(OfficialAgentImageResolver.forCurrentServer(DockerRegistryClientFactory.getDefault()),
             buildServer, webLinks, TEST_DEFAULT_IDLE_TIME,
             CLEANUP_DEFAULT_TASK_RATE);
    }


    DefaultContainerTestManager(DockerImageNameResolver imageNameResolver,
                                SBuildServer buildServer,
                                WebLinks webLinks) {
        this(imageNameResolver, buildServer, webLinks, TEST_DEFAULT_IDLE_TIME,
                CLEANUP_DEFAULT_TASK_RATE);
    }


    DefaultContainerTestManager(DockerImageNameResolver imageNameResolver,
                                SBuildServer buildServer, WebLinks
                                        webLinks,
                                Duration testMaxIdleTime, Duration cleanupRate) {
        this.imageNameResolver = imageNameResolver;
        this.testMaxIdleTime = testMaxIdleTime;
        this.cleanupRate = cleanupRate;
        this.buildServer = buildServer;
        this.webLinks = webLinks;

        // TODO: remove on dispose
        buildServer.addListener(new ServerListener());

        cleanUpTestAgents();
    }

    @Nonnull
    @Override
    public UUID createNewTestContainer(@Nonnull DockerCloudClientConfig clientConfig,
                                       @Nonnull DockerImageConfig imageConfig) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client configuration cannot be null.");
        DockerCloudUtils.requireNonNull(imageConfig, "Image configuration cannot be null.");

        DefaultAgentHolderTestHandler test = newTestInstance(clientConfig);

        URL serverURL = clientConfig.getServerURL();
        String serverURLStr = serverURL != null ? serverURL.toString() : webLinks.getRootUrl();

        CreateAgentHolderTestTask testTask = new CreateAgentHolderTestTask(test, imageConfig, serverURLStr, test
                .getUuid(), imageNameResolver);
        test.setCurrentTaskFuture(schedule(testTask));

        return test.getUuid();
    }

    @Override
    public void startTestContainer(@Nonnull UUID testUuid) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");

        DefaultAgentHolderTestHandler test = retrieveTestInstance(testUuid);

        String containerId = test.getContainerId();

        if (containerId == null) {
            throw new ContainerTestException("Container not created.");
        }

        assert containerId != null;

        StartAgentHolderTestTask testTask = new StartAgentHolderTestTask(test, containerId, test.getUuid());
        test.setCurrentTaskFuture(schedule(testTask));
    }

    private static final Pattern VT100_ESCAPE_PTN = Pattern.compile("\u001B\\[[\\d;]*[^\\d;]");

    @Nonnull
    @Override
    public String getLogs(@Nonnull UUID testUuid) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");

        DefaultAgentHolderTestHandler test = retrieveTestInstance(testUuid);

        String containerId = test.getContainerId();

        if (containerId == null) {
            throw new ContainerTestException("Container not created.");
        }

        CharSequence logs = test.getDockerClientFacade().getLogs(containerId);

        return VT100_ESCAPE_PTN.matcher(logs).replaceAll("");
    }

    @Override
    public void dispose(@Nonnull UUID testUuid) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");

        DefaultAgentHolderTestHandler test = lock.call(() -> tests.get(testUuid));

        if (test == null) {
            return;
        }

        dispose(test);
    }

    @Override
    public void setListener(@Nonnull UUID testUuid, @Nonnull ContainerTestListener listener) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");
        DockerCloudUtils.requireNonNull(listener, "Test listener cannot be null.");
        DefaultAgentHolderTestHandler test = retrieveTestInstance(testUuid);
        test.setListener(listener);
    }

    @Nonnull
    @Override
    public Optional<TestContainerStatusMsg> retrieveStatus(UUID testUuid) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");
        DefaultAgentHolderTestHandler test = retrieveTestInstance(testUuid);
        test.notifyInteraction();
        return test.getLastStatusMsg();
    }

    private DefaultAgentHolderTestHandler retrieveTestInstance(UUID testUuid) {

        DefaultAgentHolderTestHandler test = lock.call(() -> tests.get(testUuid));

        if (test == null) {
            throw new ContainerTestException("Bad or expired token: " + testUuid);
        }

        return test;
    }

    private void activate() {
        if (executorService == null) {
            lock.run(() -> {
                executorService = createScheduledExecutor();
                executorService.scheduleWithFixedDelay(new CleanupTask(), cleanupRate.toNanos(), cleanupRate.toNanos(),
                        TimeUnit.NANOSECONDS);
            });
        }
    }

    private void passivate() {
        if (executorService != null) {
            lock.run(() -> {
                executorService.shutdownNow();
                executorService = null;
            });
        }
    }

    private void dispose(DefaultAgentHolderTestHandler test) {

        LOG.info("Disposing test task: " + test.getUuid());

        DockerClientFacade clientFacade;
        Optional<ContainerTestListener> statusListener;
        String containerId;

        lock.run(() -> {
            tests.remove(test.getUuid());

            if (tests.isEmpty() && agentToRemove.isEmpty()) {
                passivate();
            }

            cancelFutureQuietly(test.getCurrentTaskFuture());
        });

        clientFacade = test.getDockerClientFacade();
        statusListener = test.getTestListener();
        containerId = test.getContainerId();


        // Dispose all IO-bound resources without locking.
        statusListener.ifPresent(ContainerTestListener::disposed);

        if (containerId != null) {
            try {
                clientFacade.terminateAgentContainer(containerId, Duration.ofSeconds(10), true);
            } catch (DockerClientException e) {
                // Ignore;
            } catch (Exception e) {
                LOG.error("Unexpected error while disposing test instance: " + test.getUuid(), e);
            }
        }

        clientFacade.close();

        cleanUpTestAgents();
    }

    private void cancelFutureQuietly(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private DefaultAgentHolderTestHandler newTestInstance(DockerCloudClientConfig clientConfig) {
        return lock.call(() -> {
            DefaultAgentHolderTestHandler test = DefaultAgentHolderTestHandler.newTestInstance(clientConfig);

            boolean duplicate = tests.put(test.getUuid(), test) != null;
            assert !duplicate;

            return test;
        });
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

                if (task instanceof AgentHolderTestTask) {

                    AgentHolderTestTask agentHolderTestTask = (AgentHolderTestTask) task;
                    DefaultAgentHolderTestHandler test = (DefaultAgentHolderTestHandler) agentHolderTestTask
                            .getTestTaskHandler();

                    if (t == null && future.isDone()) {
                        try {
                            future.get();
                        } catch (Exception e) {
                            t = e;
                        }
                    }
                    if (t == null) {
                        if (agentHolderTestTask.getStatus() == TestContainerStatusMsg.Status.PENDING) {
                            schedule(task, REFRESH_TASK_RATE.toNanos(), TimeUnit.NANOSECONDS);
                        }
                    } else if (t instanceof InterruptedException || t instanceof CancellationException) {
                        // Cancelled task, ignore.
                        LOG.info(test.getUuid() + " was interrupted.", t);
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

    private <T extends AgentHolderTestTask> ScheduledFutureWithRunnable<T> schedule(T task) {
        return lock.call(() -> {
            if (executorService == null) {
                activate();
            }

            @SuppressWarnings("unchecked")
            ScheduledFutureWithRunnable<T> futureTask = (ScheduledFutureWithRunnable<T>) executorService
                    .submit(task);
            return futureTask;
        });
    }

    private class CleanupTask implements Runnable {

        @Override
        public void run() {

            List<DefaultAgentHolderTestHandler> toDispose = new ArrayList<>();

            lock.run(() -> {
                for (DefaultAgentHolderTestHandler test : DefaultContainerTestManager.this.tests.values()) {
                    if (test.getCurrentTaskFuture() != null) {
                        if (Duration.between(test.getLastInteraction(), Instant.now()).compareTo
                                (testMaxIdleTime) > 0) {
                            toDispose.add(test);
                        }
                    }
                }
            });

            for (DefaultAgentHolderTestHandler test : toDispose) {
                dispose(test);
            }

            cleanUpTestAgents();
        }
    }

    private void cleanUpTestAgents() {

        BuildAgentManager agentMgr = buildServer.getBuildAgentManager();
        List<? extends SBuildAgent> agents;
        if (agentMgr instanceof BuildAgentManagerEx) {
            agents = ((BuildAgentManagerEx) agentMgr).getUnregisteredAgents(true);
        } else {
            agents = agentMgr.getUnregisteredAgents();
        }
        for (SBuildAgent agent : agents) {
            String uuidStr = DockerCloudUtils.getEnvParameter(agent, DockerCloudUtils.ENV_TEST_INSTANCE_ID);
            UUID instanceUuid = DockerCloudUtils.tryParseAsUUID(uuidStr);
            if (instanceUuid != null) {

                boolean removeAgent = lock.call(() -> {
                    if (!tests.containsKey(instanceUuid)) {
                        if (agent.isRegistered()) {
                            agentToRemove.add(instanceUuid);
                            activate();
                        } else {
                            agentToRemove.remove(instanceUuid);
                            return true;
                        }
                    }
                    return false;
                });

                if (removeAgent) {
                    try {
                        agentMgr.removeAgent(agent, null);
                    } catch (AgentCannotBeRemovedException e) {
                        LOG.warn("Cannot remove agent: " + agent, e);
                    }
                }
            }
        }

        lock.run(() -> {
            if (tests.isEmpty() && agentToRemove.isEmpty()) {
                passivate();
            }
        });

    }

    @Override
    public void dispose() {
        lock.run(() -> {
            if (disposed) {
                return;
            }

            for (DefaultAgentHolderTestHandler test : new ArrayList<>(tests.values())) {
                dispose(test);
            }

            passivate();

            disposed = true;
        });
    }

    private class ServerListener extends BuildServerAdapter {

        @Override
        public void agentRegistered(@Nonnull SBuildAgent agent, long currentlyRunningBuildId) {
            // We attempt here to disable the agent as soon as possible to prevent it from starting any job.
            UUID testInstanceUuid = DockerCloudUtils.tryParseAsUUID(DockerCloudUtils.getEnvParameter(agent,
                    DockerCloudUtils.ENV_TEST_INSTANCE_ID));

            if (testInstanceUuid != null) {
                agent.setEnabled(false, null, "Docker cloud test instance.");
                lock.run(() -> {
                    agentToRemove.add(testInstanceUuid);
                    activate();
                    DefaultAgentHolderTestHandler test = tests.get(testInstanceUuid);
                    if (test != null) {
                        test.setBuildAgentDetected(true);
                    }
                });
            }
        }
    }
}
