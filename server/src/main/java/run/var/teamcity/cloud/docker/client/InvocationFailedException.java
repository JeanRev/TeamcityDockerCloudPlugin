package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when a Docker client invocation failed on the server side.
 */
public class InvocationFailedException extends DockerClientException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    public InvocationFailedException(@Nullable String msg) {
        super(msg);
    }

}
