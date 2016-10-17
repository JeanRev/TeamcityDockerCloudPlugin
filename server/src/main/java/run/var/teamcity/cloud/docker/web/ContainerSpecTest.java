package run.var.teamcity.cloud.docker.web;


import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClient;
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
    private final DockerCloudClientConfig clientConfig;
    private final DockerClient client;
    private final BuildAgentManager agentMgr;
    private long lastInteraction;

    private String containerId;
    private AtmosphereResource atmosphereResource;
    private TestContainerStatusMsg statusMsg;
    private ScheduledFutureWithRunnable<? extends ContainerTestTask> currentTaskFuture = null;

    private ContainerSpecTest(Broadcaster broadcaster, DockerCloudClientConfig clientConfig, DockerClient client,
                              BuildAgentManager agentMgr) {
        assert broadcaster != null && clientConfig != null && client != null && agentMgr != null;

        this.broadcaster = broadcaster;
        this.clientConfig = clientConfig;
        this.client = client;
        this.agentMgr = agentMgr;

        notifyInteraction();
    }

    public static ContainerSpecTest newTestInstance(@NotNull Broadcaster broadcaster,
                                                    @NotNull DockerCloudClientConfig clientConfig,
                                                    @NotNull BuildAgentManager agentMgr) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null");
        DockerCloudUtils.requireNonNull(agentMgr, "Agent manager cannot be null");
        DockerClient client = DockerClient.open(clientConfig.getInstanceURI(), 1);
        return new ContainerSpecTest(broadcaster, clientConfig, client, agentMgr);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getContainerId() {
        lock.lock();
        try {
            return containerId;
        } finally {
            lock.unlock();
        }
    }

    public AtmosphereResource getAtmosphereResource() {
        lock.lock();
        try {
            return atmosphereResource;
        } finally {
            lock.unlock();
        }
    }

    public ScheduledFutureWithRunnable<? extends ContainerTestTask> getCurrentTaskFuture() {
        lock.lock();
        try {
            return currentTaskFuture;
        }finally {
            lock.unlock();
        }
    }

    @Override
    public DockerClient getDockerClient() {
        return client;
    }

    public void notifyInteraction() {
        lastInteraction = System.nanoTime();
    }

    public long getLastInteraction() {
        return lastInteraction;
    }

    public void setCurrentTask(ScheduledFutureWithRunnable<? extends ContainerTestTask>
            currentTask) {
        lock.lock();
        try {
            this.currentTaskFuture = currentTask;
        } finally {
            lock.unlock();
        }
        notifyStatus(currentTaskFuture.getTask().getPhase(), Status.PENDING, "", null);

    }

    public void setAtmosphereResource(AtmosphereResource atmosphereResource) {
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
    public void notifyContainerId(String containerId) {
        lock.lock();
        try {
            this.containerId = containerId;
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void notifyStatus(Phase phase, Status status, String msg, Throwable failure) {
        statusMsg = new TestContainerStatusMsg(uuid, phase, status, msg, failure);
        broadcastStatus();
    }

    private void broadcastStatus() {
        AtmosphereResource atmosphereResource = getAtmosphereResource();
        if (atmosphereResource != null) {
            broadcaster.broadcast(new XMLOutputter().outputString(statusMsg.toExternalForm()), atmosphereResource);
        }
    }

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
