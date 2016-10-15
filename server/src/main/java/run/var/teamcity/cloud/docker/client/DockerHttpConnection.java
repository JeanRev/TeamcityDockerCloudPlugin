package run.var.teamcity.cloud.docker.client;


import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.impl.conn.DefaultManagedHttpClientConnection;
import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.ChunkedOutputStream;
import org.apache.http.impl.io.IdentityInputStream;
import org.apache.http.impl.io.IdentityOutputStream;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by jr on 27.08.16.
 */
public class DockerHttpConnection extends DefaultManagedHttpClientConnection {

    public DockerHttpConnection(String id, int buffersize) {
        super(id, buffersize);
    }

    private volatile InputStream is;

    @Override
    protected OutputStream createOutputStream(final long len, final SessionOutputBuffer outbuffer) {

        if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedOutputStream(4096, outbuffer);
        }
        return super.createOutputStream(len, outbuffer);
        //return new IdentityOutputStream(getSessionOutputBuffer());
    }

    public InputStream prepareInputStream() {
        //return super.createInputStream(ContentLengthStrategy.IDENTITY, getSessionInputBuffer());
        //return new ChunkedInputStream(getSessionInputBuffer(), null);
        return super.createInputStream(ContentLengthStrategy.CHUNKED, getSessionInputBuffer());
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
