package run.var.teamcity.cloud.docker.client.npipe;


import java.net.SocketException;

/**
 * Exception thrown when attempting to use an unsupported socket implementation.
 */
public class UnsupportedSocketOperationException extends SocketException {

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param msg the message (may be null)
     */
    public UnsupportedSocketOperationException(String msg) {
        super(msg);
    }
}
