package run.var.teamcity.cloud.docker.web;


import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Container test manager.
 */
interface ContainerTestManager {

    /**
     * Create a new test container.
     *
     * @param clientConfig the cloud client configuration
     * @param imageConfig the image configuration from which the test container will be created
     *
     * @return the create test UUID
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws ContainerTestException if an error prevented the container creation
     */
    @Nonnull
    UUID createNewTestContainer(@Nonnull DockerCloudClientConfig clientConfig,
                                         @Nonnull DockerImageConfig imageConfig);
    /**
     * Start the test container for the given test UUID.
     *
     * @param testUuid the test UUID
     *
     * @throws NullPointerException if {@code testUuid} is {@code null}
     * @throws ContainerTestException if an error prevented the container from being started
     */
    void startTestContainer(@Nonnull UUID testUuid);

    /**
     * Gets the containers logs for the given test UUID.
     *
     * @param testUuid the test UUID
     *
     * @return the container logs
     *
     * @throws NullPointerException if {@code testUuid} is {@code null}
     * @throws ContainerTestException if an error prevented querying the container logs
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
    void setListener(@Nonnull UUID testUuid, @Nonnull ContainerTestListener listener);

    @Nonnull
    Optional<TestContainerStatusMsg> retrieveStatus(UUID testUuid);

    /**
     * Dispose the test manager.
     */
    void dispose();

}
