package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when trying to stop a stopped container.
 */
public class ContainerAlreadyStoppedException extends InvocationFailedException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    public ContainerAlreadyStoppedException(@Nullable String msg) {
        super(msg);
    }
}
