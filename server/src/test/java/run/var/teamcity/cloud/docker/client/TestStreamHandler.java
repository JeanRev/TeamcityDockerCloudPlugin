package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.util.LockHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class TestStreamHandler implements StreamHandler {

    private final LockHandler lock = LockHandler.newReentrantLock();
    private final OutputStream outputStream;
    private final List<StreamFragment> fragments = new ArrayList<>();

    private Iterator<StreamFragment> iterator;
    private boolean closed = false;

    public TestStreamHandler(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Nullable
    @Override
    public StdioInputStream getNextStreamFragment() throws IOException {
        return lock.callChecked(() -> {
            if (closed) {
                throw new IOException("Handler closed.");
            }
            if (iterator == null) {
                iterator = fragments.iterator();
            }

            if (iterator.hasNext()) {
                StreamFragment fragment = iterator.next();
                return new StdioInputStream(new ByteArrayInputStream(fragment.content.getBytes(StandardCharsets.UTF_8)),
                        fragment.type);
            }
            return null;
        });
    }

    @Nonnull
    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        lock.runChecked(() -> {
            outputStream.close();
            closed = true;
        });
    }

    public TestStreamHandler fragment(String content, StdioType type) {
        lock.run(() -> {
            assertThat(iterator).isNull();
            fragments.add(new StreamFragment(content, type));
        });
        return this;
    }

    private class StreamFragment {
        private final String content;
        private final StdioType type;

        StreamFragment(String content, StdioType type) {
            this.content = content;
            this.type = type;
        }
    }
}
