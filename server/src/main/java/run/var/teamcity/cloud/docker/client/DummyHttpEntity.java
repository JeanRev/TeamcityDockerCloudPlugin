package run.var.teamcity.cloud.docker.client;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by jr on 29.08.16.
 */
public class DummyHttpEntity implements HttpEntity {
    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public long getContentLength() {
        return 0;
    }

    @Override
    public Header getContentType() {
        return null;
    }

    @Override
    public Header getContentEncoding() {
        return null;
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        throw new IOException();
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    @Override
    public void consumeContent() throws IOException {
        // Nothing to do
    }
}
