package run.var.teamcity.cloud.docker.client;

import org.apache.http.HttpClientConnection;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.impl.conn.DefaultManagedHttpClientConnection;
import org.apache.http.impl.io.ChunkedOutputStream;
import org.apache.http.io.SessionOutputBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Custom {@link HttpClientConnection} to access the response input and output streams.
 */
public class DockerHttpConnection extends DefaultManagedHttpClientConnection {

    public DockerHttpConnection(String id, int buffersize) {
        super(id, buffersize);
    }

    @Override
    protected OutputStream createOutputStream(final long len, final SessionOutputBuffer outbuffer) {

        if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedOutputStream(4096, outbuffer);
        }
        return super.createOutputStream(len, outbuffer);
    }

    public OutputStream prepareOutputStream() {
        return super.createOutputStream(ContentLengthStrategy.IDENTITY, getSessionOutputBuffer());
    }

    @Override
    public InputStream getSocketInputStream(Socket socket) throws IOException {
        return super.getSocketInputStream(socket);
    }

    @Override
    public OutputStream getSocketOutputStream(Socket socket) throws IOException {
        return super.getSocketOutputStream(socket);
    }
}
