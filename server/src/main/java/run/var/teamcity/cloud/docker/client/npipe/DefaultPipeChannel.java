package run.var.teamcity.cloud.docker.client.npipe;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

/**
 * Pipe channel wrapping an {@link AsynchronousFileChannel}. Asynchronous file channels are well suited since they
 * provide timeout handling for free, and are highly efficient (will leverage IOCP when available).
 */
class DefaultPipeChannel implements PipeChannel {

    private final AsynchronousFileChannel wrappedChannel;

    private DefaultPipeChannel(AsynchronousFileChannel wrappedChannel) {
        assert wrappedChannel != null;
        this.wrappedChannel = wrappedChannel;
    }

    /**
     * Open a new pipe channel from the given path.
     *
     * @param path the pipe path
     *
     * @return the new channel
     *
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException if any I/O error occurred
     */
    static PipeChannel open(Path path) throws IOException {
        DockerCloudUtils.requireNonNull(path, "Path cannot be null.");

        return new DefaultPipeChannel(AsynchronousFileChannel.open(path, StandardOpenOption.READ,
                StandardOpenOption.WRITE));
    }

    // Reading and writing: no tracking of the file position will be performed since named pipe are "self-truncating"
    // (every byte read will reduce the number of bytes left, and every byte written cannot be read back from this
    // channel). Every accesses are therefore performed at position 0.

    @Override
    public Future<Integer> read(ByteBuffer bb) {
        return wrappedChannel.read(bb, 0);
    }

    @Override
    public Future<Integer> write(ByteBuffer bb) {
        return wrappedChannel.write(bb, 0);
    }

    @Override
    public boolean isOpen() {
        return wrappedChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        wrappedChannel.close();
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min((long) Integer.MAX_VALUE, wrappedChannel.size());
    }
}
