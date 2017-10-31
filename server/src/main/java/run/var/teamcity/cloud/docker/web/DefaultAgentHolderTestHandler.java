package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerCloudSupport;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;
import run.var.teamcity.cloud.docker.util.Resources;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.web.TestAgentHolderStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestAgentHolderStatusMsg.Status;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link AgentHolderTestHandler} implementation.
 */
public class DefaultAgentHolderTestHandler implements AgentHolderTestHandler {

    private final UUID uuid = UUID.randomUUID();
    private final LockHandler lock = LockHandler.newReentrantLock();
    private final DockerClientFacade clientFacade;
    private final Resources resources;

    @Nullable
    private AgentHolderTestListener testListener;
    private Instant lastInteraction;
    @Nullable
    private TestAgentHolderStatusMsg lastStatusMsg;
    @Nullable
    private String agentHolderId;
    @Nullable
    private Instant agentHolderStartTime;
    private boolean logsAvailable = false;
    private boolean buildAgentDetected = false;

    private ScheduledFutureWithRunnable<? extends AgentHolderTestTask> currentTaskFuture = null;

    private DefaultAgentHolderTestHandler(DockerClientFacade clientFacade, Resources resources) {
        assert clientFacade != null && resources != null;

        this.clientFacade = clientFacade;
        this.resources = resources;

        notifyInteraction();
    }

    public static DefaultAgentHolderTestHandler newTestInstance(@Nonnull DockerCloudClientConfig clientConfig) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");
        DockerCloudSupport cloudSupport = clientConfig.getCloudSupport();
        DockerClientFacade clientFacade = cloudSupport.createClientFacade(clientConfig.getDockerClientConfig()
                        .connectionPoolSize(1));
        return new DefaultAgentHolderTestHandler(clientFacade, cloudSupport.resources());
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
     * Gets the agent holder ID associated with this test. May be empty if the agent holder creation did not succeed
     * yet.
     *
     * @return the agent holder ID
     */
    @Nonnull
    public Optional<String> getAgentHolderId() {
        return Optional.ofNullable(lock.call(() -> agentHolderId));
    }

    /**
     * Gets the agent holder start time. Will be empty if the agent holder did started yet.
     *
     * @return the agent start time
     */
    @Nonnull
    public Optional<Instant> getAgentHolderStartTime() {
        return Optional.ofNullable(agentHolderStartTime);
    }

    /**
     * Returns {@code true} if logs are available for the agent holder. Implies that the agent holder started and
     * support fetching logs.
     *
     * @return {@code true} if logs are available
     */
    public boolean isLogsAvailable() {
        return logsAvailable;
    }

    /**
     * Gets the listener associated with this test if any.
     *
     * @return the listener if any
     */
    @Nonnull
    public Optional<AgentHolderTestListener> getTestListener() {
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

    @Nonnull
    @Override
    public Resources getResources() {
        return resources;
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
    @Nonnull
    public Optional<ScheduledFutureWithRunnable<? extends AgentHolderTestTask>> getCurrentTaskFuture() {
        return Optional.ofNullable(lock.call(() -> currentTaskFuture));
    }

    /**
     * Sets the current task associated with this test.
     *
     * @param currentTask the test task
     *
     * @throws NullPointerException if {@code currentTask} is {@code null}
     */
    public void setCurrentTaskFuture(@Nonnull ScheduledFutureWithRunnable<? extends AgentHolderTestTask> currentTask) {
        DockerCloudUtils.requireNonNull(currentTask, "Current task cannot be null.");
        lock.run(() -> {
            this.currentTaskFuture = currentTask;
            notifyInteraction();
        });
    }

    @Override
    public void notifyAgentHolderId(@Nonnull String agentHolderId) {
        DockerCloudUtils.requireNonNull(agentHolderId, "Container ID cannot be null.");

        lock.run(() -> this.agentHolderId = agentHolderId);
    }

    @Override
    public void notifyAgentHolderStarted(@Nonnull Instant agentHolderStartTime, boolean logsAvailable) {
        DockerCloudUtils.requireNonNull(agentHolderStartTime, "Start time cannot be null.");

        lock.run(() -> {
            this.agentHolderStartTime = agentHolderStartTime;
            this.logsAvailable = logsAvailable;
        });
    }

    public void setListener(@Nonnull AgentHolderTestListener testListener) {
        DockerCloudUtils.requireNonNull(testListener, "Test listener cannot be null.");

        TestAgentHolderStatusMsg lastStatusMsg = lock.call(() -> {
            this.testListener = testListener;
            return this.lastStatusMsg;
        });

        if (lastStatusMsg != null) {
            testListener.notifyStatus(lastStatusMsg);
        }
    }

    public Optional<TestAgentHolderStatusMsg> getLastStatusMsg() {
        return Optional.ofNullable(lock.call(() -> lastStatusMsg));
    }

    @Override
    public void notifyStatus(@Nonnull Phase phase, @Nonnull Status status, @Nullable String msg,
            @Nullable Throwable failure, @Nonnull List<String> warnings) {
        DockerCloudUtils.requireNonNull(phase, "Test phase cannot be null.");
        DockerCloudUtils.requireNonNull(status, "Test status cannot be null.");
        DockerCloudUtils.requireNonNull(warnings, "Warnings list cannot be null.");

        TestAgentHolderStatusMsg statusMsg = new TestAgentHolderStatusMsg(uuid, phase, status, msg, agentHolderId,
                agentHolderStartTime, logsAvailable, failure, warnings);

        lock.run(() -> this.lastStatusMsg = statusMsg);
        AgentHolderTestListener testListener = lock.call(() -> this.testListener);

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
