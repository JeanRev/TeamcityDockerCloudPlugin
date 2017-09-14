package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.util.LockHandler;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TestStreamHandlerFactory {

    private final LockHandler lock = LockHandler.newReentrantLock();

    private final OutputStream outputStream;
    private final List<StreamFragment> fragments = new ArrayList<>();

    public TestStreamHandlerFactory(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public StreamHandler compositeStreamHandler() {
        return new TestStreamHandler(outputStream) {

            private StdioInputStream fragment;
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                lock.run(() -> {
                    try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
                        for (StreamFragment fragment : fragments) {
                            writer.write(fragment.content);
                        }
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }

                    fragment = new StdioInputStream(new ByteArrayInputStream(baos.toByteArray()), null);
                });
            }

            @Nullable
            @Override
            public StdioInputStream getNextStreamFragment() throws IOException {
                return lock.callChecked(() -> {
                    if (closed) {
                        throw new IOException("Handler closed.");
                    }
                    StdioInputStream fragment = this.fragment;
                    this.fragment = null;
                    return fragment;
                });
            }



        };
    }

    public StreamHandler multiplexedStreamHandler() {
        return new TestStreamHandler(outputStream) {

            private Iterator<StreamFragment> iterator;
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
        };
    }



    public TestStreamHandlerFactory fragment(String content, StdioType type) {
        lock.run(() -> fragments.add(new StreamFragment(content, type)));
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
