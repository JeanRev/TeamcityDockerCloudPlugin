package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An input stream filter with a fixed capacity. Invoking {@code close()} on this filter will not close the
 * underlying input stream.
 * <p>
 *     This stream filter is thread-safe to use, as long as the underlying stream is thread-safe as well.
 * </p>
 */
class CappedInputStream extends FilterInputStream {

    private final ReentrantLock lock = new ReentrantLock();

    private final long capacity;
    private long readSoFar = 0;
    private boolean closed = false;

    /**
     * Creates a new stream filter.
     *
     * @param in the stream to wrap
     * @param capacity the stream capacity
     *
     * @throws NullPointerException if {@code in} is {@code null}
     * @throws IllegalArgumentException if {@code capacity} is smaller than 0
     */
    CappedInputStream(@NotNull InputStream in, long capacity) {
        super(in);
        DockerCloudUtils.requireNonNull(in, "Input stream cannot be null.");
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + capacity);
        }
        this.capacity = capacity;
    }

    @Override
    public int read() throws IOException {
        lock.lock();
        try {
            checkNotClosed();
            if (readSoFar < capacity) {
                int b = super.read();
                readSoFar++;
                return b;
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int available() throws IOException {
        lock.lock();
        try {
            long available = capacity - readSoFar;

            assert available >= 0;

            return (int) Math.min((long) Integer.MAX_VALUE, available);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        lock.lock();
        try {
            checkNotClosed();
            int available = available();
            if (available > 0) {
                int n = super.read(b, off, Math.min(available(), len - off));
                readSoFar += n;
                return n;
            }
            return -1;
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    /**
     *
     * @throws IOException if an error occurred while exhausting or closing the stream
     */
    void exhaustAndClose() throws IOException {
        lock.lock();
        try {
            long toSkip = capacity - readSoFar;
            assert toSkip >= 0;

            while (toSkip > 0) {
                toSkip -= skip(toSkip);
            }

            close();
        } finally {
            lock.unlock();
        }
    }
    private void checkNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed.");
        }
    }
}
