package run.var.teamcity.cloud.docker.test;

import run.var.teamcity.cloud.docker.util.LockHandler;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class TestHttpSession implements HttpSession {

    private final LockHandler lock = LockHandler.newReentrantLock();
    private final Map<String, Object> attributes = new HashMap<>();

    private boolean invalidated = false;

    @Override
    public long getCreationTime() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public String getId() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public long getLastAccessedTime() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public ServletContext getServletContext() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public int getMaxInactiveInterval() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Deprecated
    @Override
    public javax.servlet.http.HttpSessionContext getSessionContext() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public Object getAttribute(String name) {
        return lock.call(() -> {
            checkValid();
            return attributes.get(name);
        });
    }

    @Deprecated
    @Override
    public Object getValue(String name) {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Deprecated
    @Override
    public String[] getValueNames() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public void setAttribute(String name, Object value) {
        lock.run(() -> {
            checkValid();
            attributes.put(name, value);
        });
    }

    @Deprecated
    @Override
    public void putValue(String name, Object value) {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public void removeAttribute(String name) {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Deprecated
    @Override
    public void removeValue(String name) {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    @Override
    public void invalidate() {
        lock.run(() -> {
            checkValid();
            invalidated = true;
        });
    }

    @Override
    public boolean isNew() {
        throw new UnsupportedOperationException("Not a real HTTP session.");
    }

    private void checkValid() {
        assert lock.isHeldByCurrentThread();
        if (invalidated) {
            throw new IllegalStateException("Session is invalidated.");
        }
    }
}
