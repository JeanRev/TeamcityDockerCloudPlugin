package run.var.teamcity.cloud.docker.test;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Simple input stream filter counting the number of bytes consumed so far.
 * <p>
 *     For test purpose only.
 * </p>
 */
public class CountingInputStream extends FilterInputStream {

    private int readCount = 0;
    private int skipCount = 0;

    public CountingInputStream(InputStream in) {
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
}
