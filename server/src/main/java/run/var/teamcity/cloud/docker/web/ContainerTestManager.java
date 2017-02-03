package run.var.teamcity.cloud.docker.web;


import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Container test manager.
 */
abstract class ContainerTestManager {

    /**
     * Create a new test container.
     *
     * @param clientConfig the cloud client configuration
     * @param imageConfig the image configuration from which the test container will be created
     * @param listener the test listener
     *
     * @return the create test UUID
     */
    @Nonnull
    abstract UUID createNewTestContainer(@Nonnull DockerCloudClientConfig clientConfig,
                                         @Nonnull DockerImageConfig imageConfig,
                                         @Nonnull ContainerTestListener listener);
    /**
     * Start the test container for the given test UUID.
     *
     * @param testUuid the test UUID
     */
    abstract void startTestContainer(@Nonnull UUID testUuid);

    /**
     * Gets the containers logs for the given test UUID.
     *
     * @param testUuid the test UUID
     *
     * @return the container logs
     */
    @Nonnull
    public abstract String getLogs(@Nonnull UUID testUuid);

    /**
     * Dispose the test with the given UUID.
     *
     * @param testUuid the test UUID
     */
    abstract void dispose(@Nonnull UUID testUuid);

    /**
     * Notify that an interaction occurred for the given test UUUID.
     *
     * @param testUUid the test UUID
     */
    abstract void notifyInteraction(@Nonnull UUID testUUid);

    abstract void dispose();

    static class ActionException extends RuntimeException {
        final int code;
        final String message;

        ActionException(int code, String message) {
            super(message);
            this.code = code;
            this.message = message;
        }
    }

}
