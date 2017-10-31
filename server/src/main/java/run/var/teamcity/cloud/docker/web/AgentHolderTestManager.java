package run.var.teamcity.cloud.docker.web;


import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent holder test manager.
 */
interface AgentHolderTestManager {

    /**
     * Create a new test agent holder.
     *
     * @param clientConfig the cloud client configuration
     * @param imageConfig the image configuration from which the test container will be created
     *
     * @return the create test UUID
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws ContainerTestException if an error prevented the agent holder creation
     */
    @Nonnull
    UUID createNewTestContainer(@Nonnull DockerCloudClientConfig clientConfig,
                                         @Nonnull DockerImageConfig imageConfig);
    /**
     * Start the test agent holder for the given test UUID.
     *
     * @param testUuid the test UUID
     *
     * @throws NullPointerException if {@code testUuid} is {@code null}
     * @throws ContainerTestException if an error prevented the agent holder from being started
     */
    void startTestContainer(@Nonnull UUID testUuid);

    /**
     * Gets the agent holder logs for the given test UUID.
     *
     * @param testUuid the test UUID
     *
     * @return the agent holder logs
     *
     * @throws NullPointerException if {@code testUuid} is {@code null}
     * @throws ContainerTestException if an error prevented querying the agent holder logs
     */
    @Nonnull
    String getLogs(@Nonnull UUID testUuid);

    /**
     * Dispose the test with the given UUID.
     *
     * @param testUuid the test UUID
     *
     * @throws NullPointerException if {@code testUuid} is {@code null}
     */
    void dispose(@Nonnull UUID testUuid);

    /**
     * Sets the listener for the given test UUID.
     *
     * @param testUuid the test UUID
     * @param listener the test listener
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    void setListener(@Nonnull UUID testUuid, @Nonnull AgentHolderTestListener listener);

    @Nonnull
    Optional<TestAgentHolderStatusMsg> retrieveStatus(UUID testUuid);

    /**
     * Dispose the test manager.
     */
    void dispose();

}
