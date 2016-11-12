package run.var.teamcity.cloud.docker.web;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.client.DefaultDockerClient;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * {@link ContainerTestTask} to start the test container.
 */
class StartContainerTestTask extends ContainerTestTask {

    private final static int AGENT_WAIT_TIMEOUT_SEC = 120;

    private final String containerId;
    private final UUID instanceUuid;
    private long containerStartTime = -1;

    /**
     * Creates a new task instance.
     *
     * @param testTaskHandler the test task handler
     * @param containerId the ID of the container to be started
     * @param instanceUuid the container test instance UUID
     */
    StartContainerTestTask(@NotNull ContainerTestTaskHandler testTaskHandler, @NotNull String containerId,
                           @NotNull UUID instanceUuid) {
        super(testTaskHandler, Phase.START);
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        DockerCloudUtils.requireNonNull(instanceUuid, "Test instance UUID cannot be null.");
        this.containerId = containerId;
        this.instanceUuid = instanceUuid;
    }

    @Override
    Status work() {
        DockerClient client = testTaskHandler.getDockerClient();

        if (containerStartTime == -1) {
            // Container not started yet, doing it now.

            containerStartTime = System.currentTimeMillis();

            client.startContainer(containerId);

            msg("Waiting for agent to connect", Phase.WAIT_FOR_AGENT);

            return Status.PENDING;
        } else if (testTaskHandler.isBuildAgentDetected()) {
            // Build agent detected.task, 0, REFRESH_TASK_RATE_SEC, TimeUnit.SECONDS
            return Status.SUCCESS;
        }


        List<Node> containers = client.listContainersWithLabel(DockerCloudUtils.TEST_INSTANCE_ID_LABEL, instanceUuid
                .toString()).getArrayValues();
        if (containers.isEmpty()) {
            throw new ContainerTestTaskException("Container was prematurely destroyed.");
        } else if (containers.size() == 1) {
            final String state = containers.get(0).getAsString("State");
            if (state.equals("running")) {
                long timeElapsedSinceStart = System.currentTimeMillis() - containerStartTime;
                if (TimeUnit.MILLISECONDS.toSeconds(timeElapsedSinceStart) > AGENT_WAIT_TIMEOUT_SEC) {
                    throw new ContainerTestTaskException("Timeout: no agent connection after " +
                            AGENT_WAIT_TIMEOUT_SEC + " seconds.");
                }
            } else {
                throw new ContainerTestTaskException("Container exited prematurely (" + state + ")");
            }
        } else {
            assert false: "Multiple containers found for the test instance UUID: " + instanceUuid;
        }

        return Status.PENDING;
    }
}
