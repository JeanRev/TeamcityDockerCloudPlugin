package run.var.teamcity.cloud.docker.web;

import javax.annotation.Nullable;

/**
 * Exception thrown when an error occurred during a container test.
 */
public class ContainerTestException extends RuntimeException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    public ContainerTestException(@Nullable String msg) {
        super(msg);
    }
}
