package run.var.teamcity.cloud.docker.client.npipe;


import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Input stream to access an asynchronous pipe channel to a named pipe.
 */
class PipeChannelInputStream extends InputStream {

    private final PipeChannel pipeChannel;
    private volatile long readTimeoutMillis;
    private volatile boolean shutdown;

    PipeChannelInputStream(PipeChannel pipeChannel, long readTimeoutMillis) {
        DockerCloudUtils.requireNonNull(pipeChannel, "Channel cannot be null.");
        if (readTimeoutMillis < 0) {
            throw new IllegalArgumentException("Read timeout must be a positive integer.");
        }
        this.pipeChannel = pipeChannel;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public int read() throws IOException {
        byte[] b1 = new byte[1];
        int n = this.read(b1);
        if (n == 1) {
            return b1[0] & 0xff;
        }

        return -1;
    }

    @Override
    public int read(@Nonnull byte[] bs, int off, int len) throws IOException {
        DockerCloudUtils.requireNonNull(bs, "Buffer cannot be null.");
        if ((off < 0) || (off > bs.length) || (len < 0) ||
                ((off + len) > bs.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0)
            return 0;

        if (shutdown) {
            return -1;
        }

        ByteBuffer bb = ByteBuffer.wrap(bs);
        bb.position(off);
        bb.limit(Math.min(off + len, bb.capacity()));

        int n;
        try {
            n = pipeChannel.read(bb).get(readTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        } catch (TimeoutException e) {
            throw new SocketTimeoutException("Timeout while waiting for data.");
        }
        return n;
    }

    boolean isShutdown() {
        return shutdown;
    }

    public boolean isOpen() {
        return pipeChannel.isOpen();
    }

    long getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    void setReadTimeoutMillis(long readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public void shutdown() {
        this.shutdown = true;
    }

    @Override
    public int available() throws IOException {
        if (shutdown) {
            return 0;
        }
        return pipeChannel.available();
    }

    @Override
    public void close() throws IOException {
        pipeChannel.close();
    }
}
