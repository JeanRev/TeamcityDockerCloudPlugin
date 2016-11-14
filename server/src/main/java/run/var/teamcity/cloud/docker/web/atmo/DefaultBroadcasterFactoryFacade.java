package run.var.teamcity.cloud.docker.web.atmo;

import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import run.var.teamcity.cloud.docker.web.BroadcasterFactoryFacade;

public class DefaultBroadcasterFactoryFacade implements BroadcasterFactoryFacade {

    private final BroadcasterFactory broadcasterFactory;

    public DefaultBroadcasterFactoryFacade(BroadcasterFactory broadcasterFactory) {
        this.broadcasterFactory = broadcasterFactory;
    }

    @Override
    public <T extends Broadcaster> T get(Class<T> c, Object id) {
        return broadcasterFactory.get(c, id);
    }

    @Override
    public boolean remove(String id) {
        return broadcasterFactory.remove(id);
    }
}
