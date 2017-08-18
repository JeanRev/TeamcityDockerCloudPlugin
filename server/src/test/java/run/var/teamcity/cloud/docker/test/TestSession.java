package run.var.teamcity.cloud.docker.test;

import javax.websocket.CloseReason;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestSession implements Session {

    private final Map<String, List<String>> requestParameterMap = new ConcurrentHashMap<>();
    private final Map<String, Object> userProperties = new ConcurrentHashMap<>();

    @Override
    public WebSocketContainer getContainer() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public void addMessageHandler(MessageHandler handler) throws IllegalStateException {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public Set<MessageHandler> getMessageHandlers() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public void removeMessageHandler(MessageHandler handler) {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public String getProtocolVersion() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public String getNegotiatedSubprotocol() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public boolean isSecure() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public boolean isOpen() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public long getMaxIdleTimeout() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public void setMaxIdleTimeout(long milliseconds) {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public void setMaxBinaryMessageBufferSize(int length) {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public void setMaxTextMessageBufferSize(int length) {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public int getMaxTextMessageBufferSize() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public String getId() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public void close(CloseReason closeReason) throws IOException {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public URI getRequestURI() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        return Collections.unmodifiableMap(requestParameterMap);
    }

    @Override
    public String getQueryString() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public Map<String, String> getPathParameters() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return userProperties;
    }

    @Override
    public Principal getUserPrincipal() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    @Override
    public Set<Session> getOpenSessions() {
        throw new UnsupportedOperationException("Not a real session.");
    }

    public TestSession addRequestParameter(String name, String value) {
        List<String> params = requestParameterMap.get(name);
        if (params == null) {
            params = new CopyOnWriteArrayList<>();
            requestParameterMap.put(name, params);
        }

        params.add(value);
        return this;
    }
}
