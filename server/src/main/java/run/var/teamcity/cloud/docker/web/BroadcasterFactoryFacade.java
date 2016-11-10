package run.var.teamcity.cloud.docker.web;

import org.atmosphere.cpr.Broadcaster;

public interface BroadcasterFactoryFacade {

    <T extends Broadcaster> Broadcaster get(Class<T> c, Object id);

    boolean remove(String id);
}
