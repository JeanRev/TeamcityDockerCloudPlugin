package run.var.teamcity.cloud.docker.client.npipe;


import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Output stream to access an asynchronous pipe channel to a named pipe.
 */
class PipeChannelOutputStream extends OutputStream {

    private final PipeChannel pipeChannel;
    private volatile Duration writeTimeout;
    private volatile boolean shutdown;

    PipeChannelOutputStream(@Nonnull PipeChannel pipeChannel, Duration writeTimeout) {
        DockerCloudUtils.requireNonNull(pipeChannel, "File channel cannot be null.");
        if (writeTimeout.isNegative()) {
            throw new IllegalArgumentException("Write timeout must be a positive integer.");
        }
        this.pipeChannel = pipeChannel;
        this.writeTimeout = writeTimeout;
    }

    @Override
    public void write(int b) throws IOException {
        if (shutdown) {
            throw new IOException("Stream has been shut down.");
        }

        byte[] b1 = new byte[1];
        b1[0] = (byte) b;
        this.write(b1);
    }

    @Override
    public void write(@NotNull byte[] bs, int off, int len) throws IOException {
        if ((off < 0) || (off > bs.length) || (len < 0) ||
                ((off + len) > bs.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        if (shutdown) {
            throw new IOException("Stream has been shut down.");
        }

        ByteBuffer bb = ByteBuffer.wrap(bs);
        bb.limit(Math.min(off + len, bb.capacity()));
        bb.position(off);

        while (bb.remaining() > 0) {
            Future<Integer> futur = pipeChannel.write(bb);
            try {
                if (writeTimeout.isZero()) {
                    futur.get();
                } else {
                    futur.get(writeTimeout.toNanos(), TimeUnit.NANOSECONDS);
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            } catch (TimeoutException e) {
                throw new WriteTimeoutIOException("Timeout while writing data.", e);
            }
        }
    }

    void shutdown() {
        this.shutdown = true;
    }

    boolean isShutdown() {
        return shutdown;
    }

    boolean isOpen() {
        return pipeChannel.isOpen();
    }

    Duration getWriteTimeout() {
        return writeTimeout;
    }

    void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    @Override
    public void close() throws IOException {
        pipeChannel.close();
    }
}
