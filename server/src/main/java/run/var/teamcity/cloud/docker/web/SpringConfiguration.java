package run.var.teamcity.cloud.docker.web;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import run.var.teamcity.cloud.docker.util.ClassNameResolver;

import javax.servlet.ServletContext;

import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.immutableMapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

/**
 * All-purpose Spring configuration bean.
 */
@Configuration
public class SpringConfiguration {

    /**
     * Instantiates the {@link SpringWSEndpointOrchestrator} bean.
     *
     * @param servletCtx the servlet context
     * @param appCtx the Spring application context
     *
     * @return the new orchestrator
     */
    @Bean(initMethod = "deployEndpoints")
    public SpringWSEndpointOrchestrator SpringWSEndpointOrchestrator(ServletContext servletCtx, ApplicationContext
            appCtx) {
        return new SpringWSEndpointOrchestrator(ClassNameResolver.getDefault(), servletCtx, appCtx,
                immutableMapOf(
                        pair("/app/docker-cloud/ws/test-container-status", ContainerTestListenerEndpoint.class),
                        pair("/app/docker-cloud/ws/container-logs", LogsStreamingEndpoint.class)
                )
        );
    }



}
