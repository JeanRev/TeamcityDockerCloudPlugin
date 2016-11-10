package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;


public class ContainerSpecTest implements ContainerTestTaskHandler{

    private final UUID uuid = UUID.randomUUID();
    private final long startTime = System.currentTimeMillis();
    private final ReentrantLock lock = new ReentrantLock();
    private final Broadcaster broadcaster;
    private final DockerClient client;
    private final BuildAgentManager agentMgr;
    private long lastInteraction;

    private String containerId;
    private AtmosphereResource atmosphereResource;
    private TestContainerStatusMsg statusMsg = new TestContainerStatusMsg(uuid, Phase.CREATE, Status.PENDING, null,
            null);
    private ScheduledFutureWithRunnable<? extends ContainerTestTask> currentTaskFuture = null;

    private ContainerSpecTest(Broadcaster broadcaster, DockerClient client, BuildAgentManager agentMgr) {
        assert broadcaster != null && client != null && agentMgr != null;

        this.broadcaster = broadcaster;
        this.client = client;
        this.agentMgr = agentMgr;

        notifyInteraction();
    }

    public static ContainerSpecTest newTestInstance(@NotNull Broadcaster broadcaster,
                                                    @NotNull DockerCloudClientConfig clientConfig,
                                                    @NotNull BuildAgentManager agentMgr) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null");
        DockerCloudUtils.requireNonNull(agentMgr, "Agent manager cannot be null");
        DockerClient client = DockerClientFactory.getDefault().createClient(clientConfig.getDockerClientConfig()
                .threadPoolSize(1));
        return new ContainerSpecTest(broadcaster, client, agentMgr);
    }

    /**
     * Gets the test UUID.
     *
     * @return the test UUID
     *
     */
    @NotNull
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
        lock.lock();
        try {
            return containerId;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the atmosphere resource associated with this test if any.
     *
     * @return the atmosphere resource or {@code null}
     */
    @Nullable
    public AtmosphereResource getAtmosphereResource() {
        lock.lock();
        try {
            return atmosphereResource;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the task currently associated with this test. May be {@code null} if no task was associated yet.
     *
     * @return the task
     */
    @Nullable
    public ScheduledFutureWithRunnable<? extends ContainerTestTask> getCurrentTaskFuture() {
        lock.lock();
        try {
            return currentTaskFuture;
        }finally {
            lock.unlock();
        }
    }

    @NotNull
    @Override
    public DockerClient getDockerClient() {
        return client;
    }

    /**
     * Notify a user interaction for this test.
     */
    public void notifyInteraction() {
        lastInteraction = System.nanoTime();
    }

    /**
     * Gets the last user interaction for this test as a nano timestamp.
     *
     * @return a nano timestamp
     */
    public long getLastInteraction() {
        return lastInteraction;
    }

    /**
     * Sets the current task associated with this test.
     *
     * @param currentTask the test task
     *
     * @throws NullPointerException if {@code currentTask} is {@code null}
     */
    public void setCurrentTask(@NotNull ScheduledFutureWithRunnable<? extends ContainerTestTask>
            currentTask) {
        DockerCloudUtils.requireNonNull(currentTask, "Current task cannot be null.");
        lock.lock();
        try {
            this.currentTaskFuture = currentTask;
        } finally {
            lock.unlock();
        }
        notifyStatus(currentTaskFuture.getTask().getPhase(), Status.PENDING, "", null);

    }

    /**
     * Sets the atmosphere resource for the client to be notified.
     *
     * @param atmosphereResource the atmosphere resource
     *
     * @throws NullPointerException if {@code atmosphereResource} is {@code null}
     */
    public void setAtmosphereResource(@NotNull AtmosphereResource atmosphereResource) {
        DockerCloudUtils.requireNonNull(atmosphereResource, "Atmosphere resource cannot be null.");
        lock.lock();
        try {
            this.atmosphereResource = atmosphereResource;
        } finally {
            lock.unlock();
        }
        // Important: broadcast the current status as soon as a the WebSocket resource is registered (may happens some
        // time after the test action was invoked).
        broadcastStatus();
    }

    @Override
    public void notifyContainerId(@NotNull String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        lock.lock();
        try {
            this.containerId = containerId;
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void notifyStatus(@NotNull Phase phase, @NotNull Status status, @Nullable String msg,
                             @Nullable Throwable failure) {
        DockerCloudUtils.requireNonNull(phase, "Test phase cannot be null.");
        DockerCloudUtils.requireNonNull(status, "Test status cannot be null.");
        statusMsg = new TestContainerStatusMsg(uuid, phase, status, msg, failure);
        broadcastStatus();
    }

    private void broadcastStatus() {
        AtmosphereResource atmosphereResource = getAtmosphereResource();
        if (atmosphereResource != null) {
            broadcaster.broadcast(new XMLOutputter().outputString(statusMsg.toExternalForm()), atmosphereResource);
        }
    }

    /**
     * Gets the current status message for this test.
     *
     * @return the current test message
     */
    @NotNull
    public TestContainerStatusMsg getStatusMsg() {
        return statusMsg;
    }

    @Override
    public boolean isBuildAgentDetected() {
        for (SBuildAgent agent : agentMgr.getRegisteredAgents(true)) {
            UUID testInstanceUuid = DockerCloudUtils.tryParseAsUUID(DockerCloudUtils.getEnvParameter(agent,
                    DockerCloudUtils.ENV_TEST_INSTANCE_ID));
            if (uuid.equals(testInstanceUuid)) {
                return true;
            }
        }
        return false;
    }
}
