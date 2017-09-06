package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nullable;

/**
 * Exception thrown when an illegal operation is attempted on an JSON node.
 */
public class NodeProcessingException extends RuntimeException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    public NodeProcessingException(@Nullable String msg) {
        super(msg);
    }
}
