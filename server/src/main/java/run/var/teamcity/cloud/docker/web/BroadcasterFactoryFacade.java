package run.var.teamcity.cloud.docker.web;

import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;

/**
 * Facade interface for an {@link BroadcasterFactory} instance. Extracted for testing purposes.
 */
public interface BroadcasterFactoryFacade {

    /**
     * @see BroadcasterFactory#get(Class, Object)
     */
    <T extends Broadcaster> Broadcaster get(Class<T> c, Object id);

    /**
     * @see BroadcasterFactory#remove(Object)
     */
    boolean remove(String id);
}
