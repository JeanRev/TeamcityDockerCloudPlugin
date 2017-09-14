package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.util.LockHandler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public abstract class TestStreamHandler implements StreamHandler {

    private final OutputStream outputStream;

    final LockHandler lock = LockHandler.newReentrantLock();
    boolean closed = false;

    public TestStreamHandler(OutputStream outputStream) {
        assert outputStream != null;
        this.outputStream = outputStream;
    }

    @Nonnull
    @Override
    public final OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public final void close() {
        lock.run(() -> {
            try {
                outputStream.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            closed = true;
        });
    }
}
