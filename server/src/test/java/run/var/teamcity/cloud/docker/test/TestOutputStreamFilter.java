package run.var.teamcity.cloud.docker.test;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Simple output stream filter tracking various interactions.
 * <p>
 * For test purpose only.
 * </p>
 */
public class TestOutputStreamFilter extends FilterOutputStream {

    private boolean closed = false;

    public TestOutputStreamFilter(OutputStream out) {
        super(out);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        super.close();
        closed = true;
    }

    public static TestOutputStreamFilter dummy() {
        return new TestOutputStreamFilter(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                // Do nothing.
            }
        });
    }
}
