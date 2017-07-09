package run.var.teamcity.cloud.docker.client.npipe;


import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Pure-Java socket adapter to access windows named pipes.
 * <p>
 * <strong>This class only works as an adapter, that allows to integrate named pipe into socket based HTTP clients
 * libraries. It does not represents a real network socket.</strong>
 * </p>
 * <p>
 * This implementation relies on the ability of named pipe to be accessed using standard files I/O and requires
 * therefore no native code.
 * </p>
 * <p>
 * Sockets instanced from this class cannot be bound to a local address and port.
 * </p>
 */
class NPipeSocket extends Socket {

    private final NPipeSocketImpl impl;

    private volatile boolean connected;
    private volatile boolean closed;

    /**
     * Creates a new socket instance.
     *
     * @throws IOException if an error occurred while creating the socket
     */
    NPipeSocket() throws IOException {
        this(new NPipeSocketImpl());
    }

    private NPipeSocket(NPipeSocketImpl impl) throws IOException {
        super(impl);
        this.impl = impl;
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        throw new UnsupportedSocketOperationException("This type of Socket cannot be bound.");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isBound() {
        return false;
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return null;
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        DockerCloudUtils.requireNonNull(endpoint, "Address cannot be null.");
        if (!(endpoint instanceof NPipeSocketAddress)) {
            throw new IOException("Can only connect to endpoints of type "
                    + NPipeSocketAddress.class.getName());
        }

        impl.connect(endpoint, timeout);
        connected = true;
    }

    @Override
    public synchronized void close() throws IOException {
        impl.close();
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    NPipeSocketImpl getImpl() {
        return impl;
    }
}
