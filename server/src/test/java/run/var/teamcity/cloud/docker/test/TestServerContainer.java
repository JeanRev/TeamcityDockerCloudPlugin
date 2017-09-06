package run.var.teamcity.cloud.docker.test;


import run.var.teamcity.cloud.docker.util.LockHandler;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

public class TestServerContainer implements ServerContainer {

    private final List<ServerEndpointConfig> deployedConfigurations = new ArrayList<>();
    private final LockHandler deploymentLock = LockHandler.newReentrantLock();
    private DeploymentException deploymentException;

    @Override
    public void addEndpoint(Class<?> aClass) throws DeploymentException {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public synchronized void addEndpoint(ServerEndpointConfig serverEndpointConfig) throws DeploymentException {
        deploymentLock.runChecked(() -> {
            if (deploymentException != null) {
                throw deploymentException;
            }
            deployedConfigurations.add(serverEndpointConfig);
        });
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        throw new UnsupportedOperationException("Not a real server container.");

    }

    @Override
    public void setAsyncSendTimeout(long l) {
        throw new UnsupportedOperationException("Not a real server container.");

    }

    @Override
    public Session connectToServer(Object o, URI uri) throws DeploymentException, IOException {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public Session connectToServer(Class<?> aClass, URI uri) throws DeploymentException, IOException {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public Session connectToServer(Endpoint endpoint, ClientEndpointConfig clientEndpointConfig, URI uri) throws
            DeploymentException, IOException {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public Session connectToServer(Class<? extends Endpoint> aClass, ClientEndpointConfig clientEndpointConfig, URI
            uri) throws DeploymentException, IOException {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(long l) {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int i) {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int i) {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        throw new UnsupportedOperationException("Not a real server container.");
    }

    public synchronized List<ServerEndpointConfig> getDeployedConfigurations() {
        return deployedConfigurations;
    }

    public synchronized TestServerContainer deploymentException(DeploymentException deploymentException) {
        this.deploymentException = deploymentException;
        return this;
    }

    public Lock getDeploymentLock() {
        return deploymentLock;
    }
}
