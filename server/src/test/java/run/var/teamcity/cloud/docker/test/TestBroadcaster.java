package run.var.teamcity.cloud.docker.test;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterConfig;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicy;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicyListener;
import org.atmosphere.cpr.BroadcasterListener;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TestBroadcaster implements Broadcaster {
    @Override
    public Broadcaster initialize(String name, URI uri, AtmosphereConfig config) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void setSuspendPolicy(long maxSuspended, POLICY policy) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> broadcast(Object o) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> delayBroadcast(Object o) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> delayBroadcast(Object o, long delay, TimeUnit t) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> scheduleFixedBroadcast(Object o, long period, TimeUnit t) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> scheduleFixedBroadcast(Object o, long waitFor, long period, TimeUnit t) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> broadcast(Object o, AtmosphereResource resource) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> broadcastOnResume(Object o) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> broadcast(Object o, Set<AtmosphereResource> subset) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Broadcaster addAtmosphereResource(AtmosphereResource resource) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Broadcaster removeAtmosphereResource(AtmosphereResource resource) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void setBroadcasterConfig(BroadcasterConfig bc) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public BroadcasterConfig getBroadcasterConfig() {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void destroy() {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Collection<AtmosphereResource> getAtmosphereResources() {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void setScope(SCOPE scope) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public SCOPE getScope() {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void setID(String name) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public String getID() {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void resumeAll() {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void releaseExternalResources() {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void setBroadcasterLifeCyclePolicy(BroadcasterLifeCyclePolicy policy) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void addBroadcasterLifeCyclePolicyListener(BroadcasterLifeCyclePolicyListener b) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public void removeBroadcasterLifeCyclePolicyListener(BroadcasterLifeCyclePolicyListener b) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public boolean isDestroyed() {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Future<Object> awaitAndBroadcast(Object t, long time, TimeUnit timeUnit) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Broadcaster addBroadcasterListener(BroadcasterListener b) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }

    @Override
    public Broadcaster removeBroadcasterListener(BroadcasterListener b) {
        throw new UnsupportedOperationException("Not a real broadcaster.");
    }
}
