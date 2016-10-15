package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by jr on 30.08.16.
 */
class CappedInputStream extends FilterInputStream {

    private final ReentrantLock lock = new ReentrantLock();

    private final long capacity;
    private long readSoFar = 0;
    private boolean closed = false;

    CappedInputStream(InputStream in, long capacity) {
        super(in);
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + capacity);
        }
        this.capacity = capacity;
    }

    @Override
    public int read() throws IOException {
        try {
            lock.lock();
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

        try {
            lock.lock();
            long available = capacity - readSoFar;

            assert available >= 0;

            return (int) Math.min((long) Integer.MAX_VALUE, available);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        try {
            lock.lock();
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

    void exhaustAndClose() throws IOException {
        try {
            lock.lock();
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
