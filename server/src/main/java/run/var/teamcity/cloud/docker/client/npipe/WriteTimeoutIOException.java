package run.var.teamcity.cloud.docker.client.npipe;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Exception thrown when a write-operation to a socket times-out.
 *
 * @see SocketTimeoutException
 */
// Does not extends SocketException since we cannot put a throwable cause there.
public class WriteTimeoutIOException extends IOException {

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param msg the message (may be null)
     * @param cause the exception cause (may be null)
     */
    public WriteTimeoutIOException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
