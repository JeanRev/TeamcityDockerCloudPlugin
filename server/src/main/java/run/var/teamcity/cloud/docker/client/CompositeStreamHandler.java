package run.var.teamcity.cloud.docker.client;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A {@link AbstractStreamHandler} returning a single composite stream.
 */
class CompositeStreamHandler extends AbstractStreamHandler {

    private StdioInputStream compositeStream;

    CompositeStreamHandler(Closeable closeHandle, InputStream inputStream, OutputStream outputStream) {
        super(closeHandle, inputStream, outputStream);

        compositeStream = new StdioInputStream(inputStream, null);
    }

    @Nullable
    @Override
    public StdioInputStream getNextStreamFragment() throws IOException {
        StdioInputStream nextStream = compositeStream;
        compositeStream = null;
        return nextStream;
    }
}
