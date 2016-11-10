package run.var.teamcity.cloud.docker.test;

import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.websocket.WebSocketHandler;
import run.var.teamcity.cloud.docker.web.AtmosphereFrameworkFacade;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;


public class TestAtmosphereFrameworkFacade implements AtmosphereFrameworkFacade {

    private final TestBroadcasterFactoryFacade broadcasterFactoryFacade = new TestBroadcasterFactoryFacade();
    @Override
    public void addWebSocketHandler(String path, WebSocketHandler handler, AtmosphereHandler h, List<AtmosphereInterceptor> l) {

    }

    @Override
    public void doCometSupport(AtmosphereRequest req, AtmosphereResponse res) throws IOException, ServletException {

    }

    @Override
    public TestBroadcasterFactoryFacade getBroadcasterFactory() {
        return broadcasterFactoryFacade;
    }
}
