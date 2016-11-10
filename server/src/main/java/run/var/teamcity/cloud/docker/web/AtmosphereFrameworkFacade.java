package run.var.teamcity.cloud.docker.web;


import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.websocket.WebSocketHandler;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

public interface AtmosphereFrameworkFacade {
    void addWebSocketHandler(String path, WebSocketHandler handler, AtmosphereHandler h, List<AtmosphereInterceptor> l);
    void doCometSupport(AtmosphereRequest req, AtmosphereResponse res) throws IOException, ServletException;
    BroadcasterFactoryFacade getBroadcasterFactory();
}
