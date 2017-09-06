package run.var.teamcity.cloud.docker.client.npipe;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;
import java.nio.file.Path;
import java.time.Duration;

/**
 * {@link SocketImpl} for {@link NPipeSocket}.
 */
class NPipeSocketImpl extends SocketImpl {

    private final LockHandler lock = LockHandler.newReentrantLock();

    private PipeChannel pipeChannel;
    private PipeChannelInputStream input;
    private PipeChannelOutputStream output;
    private Duration timeout = Duration.ZERO;
    private boolean connected;
    private boolean closed;

    @Override
    protected void shutdownInput() throws IOException {
        lock.run(() -> {
            if (input != null) {
                input.shutdown();
            }
        });
    }

    @Override
    protected void shutdownOutput() throws IOException {
        lock.run(() -> {
            if (output != null) {
                output.shutdown();
            }
        });
    }

    @Override
    protected void create(boolean stream) throws IOException {
        // Nothing to do.
    }

    @Override
    protected void connect(String host, int port) throws IOException {
        throw new UnsupportedSocketOperationException("Connection is only possible with NPipeSocketAddress.");
    }

    @Override
    protected void connect(InetAddress address, int port) throws IOException {
        throw new UnsupportedSocketOperationException("Connection is only possible with NPipeSocketAddress.");
    }

    @Override
    protected void connect(SocketAddress address, int timeout) throws IOException {
        DockerCloudUtils.requireNonNull(address, "Address cannot be null.");
        if (!(address instanceof NPipeSocketAddress)) {
            throw new SocketException("Cannot bind to this type of address: " + address.getClass());
        }

        Path pipe = ((NPipeSocketAddress) address).getPipe();
        lock.runChecked(() -> {
            pipeChannel = DefaultPipeChannel.open(pipe);
            connected = true;
        });
    }

    @Override
    protected void bind(InetAddress host, int port) throws IOException {
        throw new UnsupportedSocketOperationException("This kind of socket cannot be bound.");
    }

    @Override
    protected void listen(int backlog) throws IOException {
        throw new UnsupportedSocketOperationException("Server socket are not supported.");
    }

    @Override
    protected void accept(SocketImpl s) throws IOException {
        // Nothing to do.
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        return lock.call(() -> {
            if (input == null) {
                input = new PipeChannelInputStream(pipeChannel, timeout);
            }
            return input;
        });
    }

    @Override
    protected OutputStream getOutputStream() throws IOException {
        return lock.call(() -> {
            if (output == null) {
                output = new PipeChannelOutputStream(pipeChannel, timeout);
            }
            return output;
        });
    }

    @Override
    protected int available() throws IOException {
        return lock.callChecked(pipeChannel::available);
    }

    @Override
    protected void close() throws IOException {
        lock.runChecked(() -> {
            if (pipeChannel != null) {
                pipeChannel.close();
                closed = true;
            }
        });
    }

    @Override
    protected void sendUrgentData(int data) throws IOException {
        throw new UnsupportedSocketOperationException("Unsupported operation.");
    }

    @Override
    public void setOption(int optID, Object value) throws SocketException {
        lock.runChecked(() -> {
            if (closed) {
                throw new SocketException("Socket is closed.");
            }
            if (optID == SocketOptions.SO_TIMEOUT) {
                if (!(value instanceof Integer || value instanceof Long)) {
                    throw new SocketException("Unsupported timeout value: " + value);
                }
                timeout = Duration.ofMillis(((Number) value).longValue());
                if (input != null) {
                    input.setReadTimeout(timeout);
                }
                if (output != null) {
                    output.setWriteTimeout(timeout);
                }
            } else {
                throw new SocketException("Unsupported socket option: " + optID);
            }
        });
    }

    @Override
    public Object getOption(int optID) throws SocketException {
        return lock.callChecked(() -> {
            if (closed) {
                throw new SocketException("Socket is closed.");
            }
            if (optID == SocketOptions.SO_TIMEOUT) {
                return timeout.toMillis();
            }
            return null;
        });
    }

    boolean isConnected() {
        return connected;
    }

    public boolean isClosed() {
        return closed;
    }
}
