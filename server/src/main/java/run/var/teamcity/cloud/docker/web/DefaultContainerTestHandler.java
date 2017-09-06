package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.DockerClientFacadeFactory;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link ContainerTestHandler} implementation.
 */
public class DefaultContainerTestHandler implements ContainerTestHandler {

    private final UUID uuid = UUID.randomUUID();
    private final LockHandler lock = LockHandler.newReentrantLock();
    private final DockerClientFacade clientFacade;

    @Nullable
    private ContainerTestListener testListener;
    private Instant lastInteraction;
    @Nullable
    private TestContainerStatusMsg lastStatusMsg;
    @Nullable
    private String containerId;
    @Nullable
    private Instant containerStartTime;
    private boolean buildAgentDetected = false;

    private ScheduledFutureWithRunnable<? extends ContainerTestTask> currentTaskFuture = null;

    private DefaultContainerTestHandler(DockerClientFacade clientFacade) {
        assert clientFacade != null;

        this.clientFacade = clientFacade;

        notifyInteraction();
    }

    public static DefaultContainerTestHandler newTestInstance(@Nonnull DockerCloudClientConfig clientConfig,
                                                              @Nonnull DockerClientFacadeFactory clientFacadeFactory) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");
        DockerCloudUtils.requireNonNull(clientFacadeFactory, "Docker client facade factory cannot be null.");
        DockerClientFacade clientFacade = clientFacadeFactory.createFacade(clientConfig.getDockerClientConfig()
                .connectionPoolSize(1));
        return new DefaultContainerTestHandler(clientFacade);
    }

    /**
     * Gets the test UUID.
     *
     * @return the test UUID
     */
    @Nonnull
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the container ID associated with this test. May be {@code null} if the container creation did not succeed
     * yet.
     *
     * @return the container ID or {@code null}
     */
    @Nullable
    public String getContainerId() {
        return lock.call(() -> containerId);
    }

    /**
     * Gets the listener associated with this test if any.
     *
     * @return the listener if any
     */
    @Nonnull
    public Optional<ContainerTestListener> getTestListener() {
        return Optional.ofNullable(lock.call(() -> testListener));
    }

    /**
     * Gets the client facade to run the test.
     *
     * @return the client facade
     */
    @Nonnull
    @Override
    public DockerClientFacade getDockerClientFacade() {
        return clientFacade;
    }

    /**
     * Notify a user interaction for this test.
     */
    public void notifyInteraction() {
        lock.run(() -> lastInteraction = Instant.now());
    }

    /**
     * Gets the last user interaction timestamp for this test.
     *
     * @return the timestamp
     */
    @Nonnull
    public Instant getLastInteraction() {
        return lock.call(() -> lastInteraction);
    }

    /**
     * Gets the task currently associated with this test. May be {@code null} if no task was associated yet.
     *
     * @return the task
     */
    @Nullable
    public ScheduledFutureWithRunnable<? extends ContainerTestTask> getCurrentTaskFuture() {
        return lock.call(() -> currentTaskFuture);
    }

    /**
     * Sets the current task associated with this test.
     *
     * @param currentTask the test task
     *
     * @throws NullPointerException if {@code currentTask} is {@code null}
     */
    public void setCurrentTaskFuture(@Nonnull ScheduledFutureWithRunnable<? extends ContainerTestTask>
                                             currentTask) {
        DockerCloudUtils.requireNonNull(currentTask, "Current task cannot be null.");
        lock.run(() -> {
            this.currentTaskFuture = currentTask;
            notifyInteraction();
        });
    }

    @Override
    public void notifyContainerId(@Nonnull String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");

        lock.run(() -> this.containerId = containerId);
    }

    @Override
    public void notifyContainerStarted(@Nonnull Instant containerStartTime) {
        DockerCloudUtils.requireNonNull(containerStartTime, "Start time cannot be null.");

        lock.run(() -> this.containerStartTime = containerStartTime);
    }

    public void setListener(@Nonnull ContainerTestListener testListener) {
        DockerCloudUtils.requireNonNull(testListener, "Test listener cannot be null.");

        TestContainerStatusMsg lastStatusMsg = lock.call(() -> {
            this.testListener = testListener;
            return this.lastStatusMsg;
        });

        if (lastStatusMsg != null) {
            testListener.notifyStatus(lastStatusMsg);
        }
    }

    public Optional<TestContainerStatusMsg> getLastStatusMsg() {
        return Optional.ofNullable(lock.call(() -> lastStatusMsg));
    }

    @Override
    public void notifyStatus(@Nonnull Phase phase, @Nonnull Status status, @Nullable String msg,
                             @Nullable Throwable failure, @Nonnull List<String> warnings) {
        DockerCloudUtils.requireNonNull(phase, "Test phase cannot be null.");
        DockerCloudUtils.requireNonNull(status, "Test status cannot be null.");
        DockerCloudUtils.requireNonNull(status, "Warnings list cannot be null.");

        TestContainerStatusMsg statusMsg = new TestContainerStatusMsg(uuid, phase, status, msg, containerId,
                containerStartTime, failure, warnings);

        lock.run(() -> this.lastStatusMsg = statusMsg);
        ContainerTestListener testListener = lock.call(() -> this.testListener);

        if (testListener != null) {
            testListener.notifyStatus(statusMsg);
        }
    }

    @Override
    public boolean isBuildAgentDetected() {
        return lock.call(() -> buildAgentDetected);
    }

    public void setBuildAgentDetected(boolean buildAgentDetected) {
        lock.run(() -> this.buildAgentDetected = buildAgentDetected);
    }
}
