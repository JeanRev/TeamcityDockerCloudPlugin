package run.var.teamcity.cloud.docker.test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Simple input stream filter tracking various interactions.
 * <p>
 *     For test purpose only.
 * </p>
 */
public class TestInputStream extends FilterInputStream {

    private int readCount = 0;
    private int skipCount = 0;
    private boolean closed = false;

    public TestInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            readCount++;
        }
        return b;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int c = super.read(b);
        if (c != -1) {
            readCount += c;
        }
        return c;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int c = super.read(b, off, len);
        if (c != -1) {
            readCount += c;
        }
        return c;
    }

    @Override
    public long skip(long n) throws IOException {
        long c = super.skip(n);
        skipCount += c;
        return c;
    }

    public int getReadCount() {
        return readCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        super.close();
        closed = true;
    }

    public static TestInputStream dummy() {
        return new TestInputStream(new ByteArrayInputStream(new byte[0]));
    }
}
