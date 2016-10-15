package run.var.teamcity.cloud.docker.web;


import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.interceptor.HeartbeatInterceptor;
import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

public class AtmosphereFrameworkHolder {

    private final AtmosphereFramework atmosphereFramework;

    public AtmosphereFrameworkHolder(@NotNull ServletConfig servletConfig) throws ServletException {

        AtmosphereFramework atmosphereFramework = new AtmosphereFramework();

        atmosphereFramework.addInitParameter("org.atmosphere.cpr.AtmosphereInterceptor.disable", HeartbeatInterceptor.class.getName());
        atmosphereFramework.addInitParameter("org.atmosphere.container.JSR356AsyncSupport.mappingPath", "/app/subscriptions");
        atmosphereFramework.addInitParameter("org.atmosphere.cpr.scanClassPath", "false");

        atmosphereFramework.getAtmosphereConfig().setSupportSession(true);

        atmosphereFramework.init(servletConfig);

        this.atmosphereFramework = atmosphereFramework;
    }

    public AtmosphereFramework getAtmosphereFramework() {
        return atmosphereFramework;
    }
}
