package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.ContainerInfo;
import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status.PENDING;
import static run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status.SUCCESS;

/**
 * {@link ContainerTestTask} to start the test container.
 */
class StartContainerTestTask extends ContainerTestTask {

    private final static Duration AGENT_WAIT_TIMEOUT = Duration.ofSeconds(120);

    private final String containerId;
    private final UUID instanceUuid;
    private Instant containerStartTime = null;

    /**
     * Creates a new task instance.
     *
     * @param testTaskHandler the test task handler
     * @param containerId     the ID of the container to be started
     * @param instanceUuid    the container test instance UUID
     */
    StartContainerTestTask(@Nonnull ContainerTestHandler testTaskHandler, @Nonnull String containerId,
                           @Nonnull UUID instanceUuid) {
        super(testTaskHandler, Phase.START);
        this.containerId = DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        this.instanceUuid = DockerCloudUtils.requireNonNull(instanceUuid, "Test instance UUID cannot be null.");
    }

    @Override
    Status work() {
        DockerClientFacade clientFacade = testTaskHandler.getDockerClientFacade();

        if (containerStartTime == null) {
            // Container not started yet, doing it now.

            containerStartTime = Instant.now();

            clientFacade.startAgentContainer(containerId);

            msg("Waiting for agent to connect", Phase.WAIT_FOR_AGENT);

            return PENDING;
        } else if (testTaskHandler.isBuildAgentDetected()) {
            return SUCCESS;
        }

        List<ContainerInfo> containers = clientFacade.listActiveAgentContainers(DockerCloudUtils
                        .TEST_INSTANCE_ID_LABEL, instanceUuid.toString());
        if (containers.isEmpty()) {
            throw new ContainerTestTaskException("Container was prematurely destroyed.");
        } else if (containers.size() == 1) {
            final ContainerInfo container = containers.get(0);
            if (container.isRunning()) {
                Duration timeElapsedSinceStart = Duration.between(containerStartTime, Instant.now());
                if (timeElapsedSinceStart.compareTo(AGENT_WAIT_TIMEOUT) > 0) {
                    throw new ContainerTestTaskException("Timeout: no agent connection after " +
                            AGENT_WAIT_TIMEOUT + " seconds.");
                }
            } else {
                throw new ContainerTestTaskException("Container exited prematurely (" + container.getState() + ")");
            }
        } else {
            assert false : "Multiple containers found for the test instance UUID: " + instanceUuid;
        }

        return PENDING;
    }
}
