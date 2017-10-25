package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import run.var.teamcity.cloud.docker.DefaultDockerCloudSupport;
import run.var.teamcity.cloud.docker.DockerCloudClientFactory;
import run.var.teamcity.cloud.docker.DockerCloudSupportRegistry;
import run.var.teamcity.cloud.docker.util.ClassNameResolver;

import javax.servlet.ServletContext;

import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.immutableMapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

/**
 * All-purpose Spring configuration bean.
 */
@Configuration
public class SpringConfiguration {

    @Bean
    public DockerCloudClientFactory vanillaCloudClientFactory(SBuildServer buildServer, PluginDescriptor pluginDescriptor, CloudRegistrar
            cloudRegistrar) {

        DockerCloudClientFactory defaultFty = new DockerCloudClientFactory(
                DefaultDockerCloudSupport.VANILLA,
                DockerCloudSupportRegistry.getDefault(),
                buildServer,
                pluginDescriptor
        );

        cloudRegistrar.registerCloudFactory(defaultFty);

        return defaultFty;
    }

    @Bean
    public DockerCloudClientFactory swarmCloudClientFactory(SBuildServer buildServer, PluginDescriptor pluginDescriptor, CloudRegistrar cloudRegistrar) {
        DockerCloudClientFactory swarmFty = new DockerCloudClientFactory(
                DefaultDockerCloudSupport.SWARM,
                DockerCloudSupportRegistry.getDefault(),
                buildServer,
                pluginDescriptor
        );

        cloudRegistrar.registerCloudFactory(swarmFty);

        return swarmFty;
    }

    @Bean
    public DockerCloudSettingsController vanillaCloudSettingsController(
            WebSocketDeploymentStatusProvider wsDeploymentStatusProvider, SBuildServer buildServer, PluginDescriptor
            pluginDescriptor, WebControllerManager webControllerManager) {

        return new DockerCloudSettingsController(
                wsDeploymentStatusProvider,
                buildServer,
                pluginDescriptor,
                webControllerManager,
                DefaultDockerCloudSupport.VANILLA.resources()
        );
    }

    @Bean
    public DockerCloudSettingsController swarmCloudSettingsController(
            WebSocketDeploymentStatusProvider wsDeploymentStatusProvider, SBuildServer buildServer, PluginDescriptor
            pluginDescriptor, WebControllerManager webControllerManager) {

        return new DockerCloudSettingsController(
                wsDeploymentStatusProvider,
                buildServer,
                pluginDescriptor,
                webControllerManager,
                DefaultDockerCloudSupport.SWARM.resources()
        );
    }

    /**
     * Instantiates the {@link SpringWSEndpointOrchestrator} bean.
     *
     * @param servletCtx the servlet context
     * @param appCtx the Spring application context
     *
     * @return the new orchestrator
     */
    @Bean(initMethod = "deployEndpoints")
    public SpringWSEndpointOrchestrator springWSEndpointOrchestrator(ServletContext servletCtx, ApplicationContext
            appCtx) {
        return new SpringWSEndpointOrchestrator(ClassNameResolver.getDefault(), servletCtx, appCtx,
                immutableMapOf(
                        pair("/app/docker-cloud/ws/test-container-status", ContainerTestListenerEndpoint.class),
                        pair("/app/docker-cloud/ws/container-logs", LogsStreamingEndpoint.class)
                )
        );
    }


}
