package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import org.springframework.context.ApplicationContext;
import run.var.teamcity.cloud.docker.util.ClassNameResolver;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.websocket.DeploymentException;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrate the deployment of Spring-based WebSocket endpoints.
 * <p>
 *     We cannot use annotations to deploy our endpoints since the plugin classes are not available a the time where
 *     the application is deployed, so the application cannot scan them. Instead, this class triggers a programmatic
 *     deployment of endpoint.
 * </p>
 * <p>
 *     Endpoints will be instantiated through the Spring framework according to their defined bean properties.
 * </p>
 */
public class SpringWSEndpointOrchestrator implements WebSocketDeploymentStatusProvider {

    private final static Logger LOG = DockerCloudUtils.getLogger(SpringWSEndpointOrchestrator.class);

    /**
     * Standard attribute to retrieve the {@link ServerContainer} instance.
     */
    public final static String SERVLET_CONTAINER_CLASS = "javax.websocket.server.ServerContainer";

    private final ClassNameResolver clsResolver;
    private final ServletContext servletCtx;
    private final ApplicationContext appCtx;
    private final Map<String, Class<?>> endpointMappings;

    private final AtomicReference<DeploymentStatus> status = new AtomicReference<>(DeploymentStatus.NOT_PERFORMED);

    /**
     * Creates a new orchestrator instance.
     *
     * @param clsResolver the class resolver to verify if the WebSocket API is available
     * @param servletCtx the server context
     * @param appCtx the Spring application context to load the endpoint
     * @param endpointMappings the endpoint mappings (path to endpoint class)
     */
    public SpringWSEndpointOrchestrator(ClassNameResolver clsResolver, ServletContext servletCtx, ApplicationContext
            appCtx, Map<String, Class<?>> endpointMappings) {
        this.clsResolver = clsResolver;
        this.servletCtx = servletCtx;
        this.appCtx = appCtx;
        this.endpointMappings = endpointMappings;
    }

    /**
     * Deploy the endpoint. The deployment status can be at any time queried using {@link #getDeploymentStatus()}.
     *
     * @throws IllegalStateException if a deployment attempt already occurred or is still in progress
     */
    public void deployEndpoints() {

        if (!status.compareAndSet(DeploymentStatus.NOT_PERFORMED, DeploymentStatus.RUNNING)) {
            throw new IllegalStateException("Deployment already initiated (status: " + status.get() + ").");
        }

        DeploymentStatus deploymentOutcome = performDeployment();

        assert deploymentOutcome == DeploymentStatus.SUCCESS || deploymentOutcome == DeploymentStatus.ABORTED;

        status.set(deploymentOutcome);
    }

    private DeploymentStatus performDeployment() {

        // We first do some general checks to see if the JSR-356 is supported by the server. Those are basically the
        // same checks that TeamCity itself is doing with two exceptions:
        // - We do not check if websockets were disabled with the "teamcity.ui.websocket.enabled" property (it's
        //   currently unclear how we can query those properties with the API we are currently using).
        // - We do not check (at least for now) if the Tomcat (or any other application server) version is well suited
        //   to activate the WebSocket feature.
        if (!clsResolver.isInClassLoader(SERVLET_CONTAINER_CLASS, getClass().getClassLoader())) {
            LOG.info("JSR-356 API not detected on classpath. WebSocket features are disabled.");
            return DeploymentStatus.ABORTED;
        }

        ServerContainer serverContainer = (ServerContainer) servletCtx.
                getAttribute(SERVLET_CONTAINER_CLASS);

        if (serverContainer == null) {
            LOG.info("No server container for WebSocket found. WebSocket features are disabled.");
            return DeploymentStatus.ABORTED;
        }

        ServerEndpointConfig.Configurator configurator = new ServerEndpointConfig.Configurator() {
            @Override
            public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                return appCtx.getBean(endpointClass);
            }

            @Override
            public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse
                    response) {
                sec.getUserProperties().put(HttpSession.class.getName(), request.getHttpSession());
            }
        };

        try {
            for (Map.Entry<String, Class<?>> mapping : endpointMappings.entrySet()) {
                serverContainer.addEndpoint(ServerEndpointConfig.Builder.create(mapping.getValue(), mapping.getKey())
                        .configurator(configurator).build());
                LOG.info("Endpoint " + mapping.getKey() + " successfully deployed on " + mapping.getValue() + ".");
            }

            return DeploymentStatus.SUCCESS;
        } catch (DeploymentException e) {
            LOG.warn("Deployment of WebSocket endpoint failed. Disabling WebSocket features.", e);
            return DeploymentStatus.ABORTED;
        }
    }

    /**
     * Gets the deployment status.
     *
     * @return the deployment status.
     */
    @Nonnull
    public DeploymentStatus getDeploymentStatus() {
        return status.get();
    }
}
