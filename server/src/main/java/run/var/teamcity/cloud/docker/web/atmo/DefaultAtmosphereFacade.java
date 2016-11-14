package run.var.teamcity.cloud.docker.web.atmo;


import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.interceptor.HeartbeatInterceptor;
import org.atmosphere.websocket.WebSocketHandler;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.web.AtmosphereFrameworkFacade;
import run.var.teamcity.cloud.docker.web.BroadcasterFactoryFacade;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

public class DefaultAtmosphereFacade implements AtmosphereFrameworkFacade {

    private final AtmosphereFramework atmosphereFramework;

    /**
     * Creates a new holder instance.
     *
     * @param servletConfig the server servlet configuration
     *
     * @throws ServletException if configuring the framework with the given configuration failed
     */
    public DefaultAtmosphereFacade(@NotNull ServletConfig servletConfig) throws ServletException {
        DockerCloudUtils.requireNonNull(servletConfig, "Servlet configuration cannot be null.");

        AtmosphereFramework atmosphereFramework = new AtmosphereFramework();

        atmosphereFramework.addInitParameter("org.atmosphere.cpr.AtmosphereInterceptor.disable", HeartbeatInterceptor.class.getName());
        atmosphereFramework.addInitParameter("org.atmosphere.container.JSR356AsyncSupport.mappingPath", "/app/subscriptions");
        atmosphereFramework.addInitParameter("org.atmosphere.cpr.scanClassPath", "false");

        atmosphereFramework.getAtmosphereConfig().setSupportSession(true);

        atmosphereFramework.init(servletConfig);

        this.atmosphereFramework = atmosphereFramework;
    }

    @Override
    public void addWebSocketHandler(String path, WebSocketHandler handler, AtmosphereHandler h, List<AtmosphereInterceptor> l) {
        atmosphereFramework.addWebSocketHandler(path, handler, h, l);
    }

    @Override
    public void doCometSupport(AtmosphereRequest req, AtmosphereResponse res) throws IOException, ServletException {
        atmosphereFramework.doCometSupport(req, res);
    }

    @Override
    public BroadcasterFactoryFacade getBroadcasterFactory() {
        return new DefaultBroadcasterFactoryFacade(atmosphereFramework.getBroadcasterFactory());
    }


}
