package run.var.teamcity.cloud.docker.client;

import org.apache.http.MalformedChunkCodingException;
import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Base class for {@link StreamHandler}s.
 */
abstract class AbstractStreamHandler implements StreamHandler {

    private final Closeable closeHandle;
    private final InputStream inputStream;
    private final OutputStream outputStream;


    AbstractStreamHandler(@Nonnull Closeable closeHandle, @Nonnull InputStream inputStream, @Nonnull OutputStream outputStream) {
        this.closeHandle = DockerCloudUtils.requireNonNull(closeHandle, "Close handle cannot be null.");
        this.inputStream = DockerCloudUtils.requireNonNull(inputStream, "Source input stream cannot be null.");
        this.outputStream = DockerCloudUtils.requireNonNull(outputStream, "Source output stream cannot be null.");
    }

    @Override
    @Nullable
    public abstract StdioInputStream getNextStreamFragment() throws IOException;

    @Override
    @Nonnull
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() {
        IOException failure = null;

        for (Closeable closeable : Arrays.asList(inputStream, outputStream, closeHandle))  {
            failure = close(failure, closeable);
        }

        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }

    private IOException close(IOException failure, Closeable closeable) {
        try {
            closeable.close();
        } catch (MalformedChunkCodingException e) {
            // May happen if the stream was not fully exhausted at the time the connection is closed. Ignore.
        } catch (IOException e) {
            if (failure == null) {
                failure = new IOException("Failed to close some stream.");
            }
            failure.addSuppressed(e);
        }
        return failure;
    }
}