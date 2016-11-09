package run.var.teamcity.cloud.docker.web;


import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.interceptor.HeartbeatInterceptor;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 * Helper class to retrieve our custom {@link AtmosphereFramework} instance with Spring.
 */
public class AtmosphereFrameworkHolder {

    private final AtmosphereFramework atmosphereFramework;

    /**
     * Creates a new holder instance.
     *
     * @param servletConfig the server servlet configuration
     *
     * @throws ServletException if configuring the framework with the given configuration failed
     */
    public AtmosphereFrameworkHolder(@NotNull ServletConfig servletConfig) throws ServletException {
        DockerCloudUtils.requireNonNull(servletConfig, "Servlet configuration cannot be null.");

        AtmosphereFramework atmosphereFramework = new AtmosphereFramework();

        atmosphereFramework.addInitParameter("org.atmosphere.cpr.AtmosphereInterceptor.disable", HeartbeatInterceptor.class.getName());
        atmosphereFramework.addInitParameter("org.atmosphere.container.JSR356AsyncSupport.mappingPath", "/app/subscriptions");
        atmosphereFramework.addInitParameter("org.atmosphere.cpr.scanClassPath", "false");

        atmosphereFramework.getAtmosphereConfig().setSupportSession(true);

        atmosphereFramework.init(servletConfig);

        this.atmosphereFramework = atmosphereFramework;
    }

    /**
     * Gets our framework instance.
     *
     * @return the framework instance
     */
    @NotNull
    public AtmosphereFramework getAtmosphereFramework() {
        return atmosphereFramework;
    }
}
