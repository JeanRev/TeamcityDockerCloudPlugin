package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class StreamHandler implements AutoCloseable {


    private final Closeable closeHandle;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    StreamHandler(Closeable closeHandle, InputStream inputStream, OutputStream outputStream) {
        this.closeHandle = closeHandle;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    @Nullable
    public abstract StdioInputStream getNextStreamFragment() throws IOException;

    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
        outputStream.close();
        closeHandle.close();
    }
}
;