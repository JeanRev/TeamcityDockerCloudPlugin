package run.var.teamcity.cloud.docker.test;

import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ManagedHttpClientConnection;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class TestManagedHttpClientConnection implements ManagedHttpClientConnection {

    private volatile Socket socket;
    private volatile int socketTimeout;

    @Override
    public String getId() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public void bind(Socket socket) throws IOException {
        this.socket = socket;
    }

    @Override
    public Socket getSocket() {
        return socket;
    }

    @Override
    public SSLSession getSSLSession() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");

    }

    @Override
    public boolean isResponseAvailable(int timeout) throws IOException {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public void sendRequestHeader(HttpRequest request) throws HttpException, IOException {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public void sendRequestEntity(HttpEntityEnclosingRequest request) throws HttpException, IOException {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public HttpResponse receiveResponseHeader() throws HttpException, IOException {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public void receiveResponseEntity(HttpResponse response) throws HttpException, IOException {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public void flush() throws IOException {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public InetAddress getLocalAddress() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public int getLocalPort() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public InetAddress getRemoteAddress() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public int getRemotePort() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public boolean isOpen() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public boolean isStale() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public void setSocketTimeout(int timeout) {
        this.socketTimeout = timeout;
    }

    @Override
    public int getSocketTimeout() {
        return socketTimeout;
    }

    @Override
    public void shutdown() throws IOException {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }

    @Override
    public HttpConnectionMetrics getMetrics() {
        throw new UnsupportedOperationException("Not a real HTTP connection.");
    }
}
