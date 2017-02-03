package run.var.teamcity.cloud.docker.web;


import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.websocket.WebSocketHandler;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

/**
 * Facade interface for an {@link AtmosphereFramework} instance. Extracted for testing purposes.
 */
public interface AtmosphereFrameworkFacade {

    /**
     * @see AtmosphereFramework#addWebSocketHandler(String, WebSocketHandler)
     */
    void addWebSocketHandler(String path, WebSocketHandler handler);

    /**
     * @see AtmosphereFramework#addWebSocketHandler(String, WebSocketHandler, AtmosphereHandler, List)
     */
    void addWebSocketHandler(String path, WebSocketHandler handler, AtmosphereHandler h, List<AtmosphereInterceptor> l);

    /**
     * @see AtmosphereFramework#doCometSupport(AtmosphereRequest, AtmosphereResponse)
     */
    void doCometSupport(AtmosphereRequest req, AtmosphereResponse res) throws IOException, ServletException;

    /**
     * @see AtmosphereFramework#getBroadcasterFactory()
     */
    BroadcasterFactoryFacade getBroadcasterFactory();
}
