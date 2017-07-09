package run.var.teamcity.cloud.docker.client.npipe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.util.concurrent.Future;

/**
 * Asynchronous channel to a windows named pipe.
 * <p>
 * The operations available here are a subset of those available on an {@link AsynchronousFileChannel}. They have
 * been extracted to an interface for clearness and better testability.
 * </p>
 */
interface PipeChannel extends AsynchronousChannel {

    /**
     * Read from the pipe channel into the given buffer.
     *
     * @param bb the buffer
     *
     * @return a {@code Future} object representing the pending result
     *
     * @throws NullPointerException if the buffer is {@code null}
     * @throws IllegalArgumentException If the position is negative or the buffer is read-only
     */
    Future<Integer> read(ByteBuffer bb);

    /**
     * Write in the pipe channel from the given buffer.
     *
     * @param bb the buffer
     *
     * @return a {@code Future} object representing the pending result
     *
     * @throws NullPointerException if the buffer is {@code null}
     * @throws IllegalArgumentException If the position is negative
     */
    Future<Integer> write(ByteBuffer bb);

    /**
     * Checks if this channel is open.
     *
     * @return {@code true} if this channel is open
     */
    boolean isOpen();

    /**
     * Gets the approximated number of bytes available for reading in this pipe.
     *
     * @return the number of bytes
     *
     * @throws IOException if an I/O error occurred
     */
    int available() throws IOException;

    @Override
    void close() throws IOException;
}
