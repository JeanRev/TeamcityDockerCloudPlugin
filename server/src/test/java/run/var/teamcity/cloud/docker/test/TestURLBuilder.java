package run.var.teamcity.cloud.docker.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class TestURLBuilder {

    private final String content;
    private IOException openStreamFailure;

    private TestURLBuilder(String content) {
        this.content = content;
    }

    public TestURLBuilder openStreamFailure(IOException openStreamFailure) {
        this.openStreamFailure = openStreamFailure;
        return this;
    }

    public static TestURLBuilder forContent(String content) {
        return new TestURLBuilder(content);
    }

    public URL build() {
        try {
            return new URL("test", null, 0, "", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) throws IOException {
                    if (openStreamFailure != null) {
                        throw openStreamFailure;
                    }
                    return new Connection(u, new TestInputStream(content));
                }
            });
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    private static class Connection extends URLConnection {

        private final InputStream inputStream;

        /**
         * Constructs a URL connection to the specified URL. A connection to
         * the object referenced by the URL is not created.
         *
         * @param url the specified URL.
         */
        protected Connection(URL url, InputStream inputStream) {
            super(url);
            this.inputStream = inputStream;
        }

        @Override
        public void connect() throws IOException {
            // Nothing to do.
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return inputStream;
        }
    }
}
