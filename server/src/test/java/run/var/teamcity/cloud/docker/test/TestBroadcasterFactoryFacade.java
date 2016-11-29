package run.var.teamcity.cloud.docker.test;

import org.atmosphere.cpr.Broadcaster;
import run.var.teamcity.cloud.docker.web.BroadcasterFactoryFacade;

public class TestBroadcasterFactoryFacade implements BroadcasterFactoryFacade {
    @Override
    public <T extends Broadcaster> Broadcaster get(Class<T> c, Object id) {
        return new TestBroadcaster();
    }

    @Override
    public boolean remove(String id) {
        return false;
    }
}
