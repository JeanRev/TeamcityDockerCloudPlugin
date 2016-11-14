package run.var.teamcity.cloud.docker.web;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.util.SimpleBroadcaster;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandlerAdapter;
import org.atmosphere.websocket.WebSocketProcessor;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.web.ContainerTestManager.Action;
import run.var.teamcity.cloud.docker.web.atmo.DefaultAtmosphereFacade;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Spring controller to manage lifecycle of container tests.
 */
public class ContainerTestController extends BaseFormXmlController {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestController.class);

    public static final String PATH = "test-container.html";

    private final ContainerTestManager testMgr;
    private final AtmosphereFrameworkFacade atmosphereFramework;
    private final Broadcaster statusBroadcaster;
    private final DockerClientFactory dockerClientFactory;

    @Autowired
    public ContainerTestController(@NotNull DefaultAtmosphereFacade atmosphereFramework,
                                   @NotNull SBuildServer server,
                                   @NotNull PluginDescriptor pluginDescriptor,
                                   @NotNull WebControllerManager manager,
                                   @NotNull WebLinks webLinks) {
        this(DockerClientFactory.getDefault(), atmosphereFramework, server, pluginDescriptor, manager,
                new DefaultContainerTestManager(OfficialAgentImageResolver.forServer(server),
                        DockerClientFactory.getDefault(), server.getBuildAgentManager(), webLinks.getRootUrl()));

    }

    ContainerTestController(@NotNull DockerClientFactory dockerClientFactory,
                            @NotNull AtmosphereFrameworkFacade atmosphereFramework,
                            @NotNull SBuildServer buildServer,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @NotNull WebControllerManager manager,
                            @NotNull ContainerTestManager testMgr) {


        this.dockerClientFactory = dockerClientFactory;
        this.testMgr = testMgr;

        buildServer.addListener(new BuildServerListener());
        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
        manager.registerController("/app/docker-cloud/test-container/**", this);

        atmosphereFramework.addWebSocketHandler("/app/docker-cloud/test-container/getStatus", new WSHandler(),
                AtmosphereFramework.REFLECTOR_ATMOSPHEREHANDLER, Collections.<AtmosphereInterceptor>emptyList());

        statusBroadcaster = atmosphereFramework.getBroadcasterFactory().get(SimpleBroadcaster.class, UUID.randomUUID());

        this.atmosphereFramework = atmosphereFramework;
    }


    @Override
    protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {

        WebUtils.configureRequestForAtmosphere(request);

        try {
            atmosphereFramework.doCometSupport(AtmosphereRequest.wrap(request), AtmosphereResponse.wrap(response));
        } catch (IOException | ServletException e) {
            LOG.error("Failed to upgrade HTTP request.", e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (IOException e1) {
                // Discard.
            }
        }

        return null;
    }

    @Override
    protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {

        String actionParam = request.getParameter("action");

        if (actionParam == null) {
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Missing action parameter");
            return;
        }

        Action action;
        try {
            action = Action.valueOf(actionParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Bad action parameter: " + actionParam);
            return;
        }

        DockerCloudClientConfig clientConfig = null;
        DockerImageConfig imageConfig = null;

        if (action == Action.CREATE) {
            Map<String, String> params = DockerCloudUtils.extractTCPluginParams(request);

            try {
                clientConfig = DockerCloudClientConfig.processParams(params, dockerClientFactory);
                imageConfig = DockerImageConfig.fromJSon(Node.parse(params.get(DockerCloudUtils.TEST_IMAGE_PARAM)));
            } catch (Exception e) {
                LOG.error("Data parsing failed.", e);
                sendErrorQuietly(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to parse data.");
                return;
            }
        }

        String uuidParam = request.getParameter("taskUuid");
        UUID testUuid = DockerCloudUtils.tryParseAsUUID(uuidParam);

        TestContainerStatusMsg statusMsg = testMgr.doAction(action, testUuid, clientConfig, imageConfig);
        xmlResponse.addContent(statusMsg.toExternalForm());
    }

    private void sendErrorQuietly(HttpServletResponse response, int sc, String msg) {
        try {
            response.setStatus(sc);
            response.setHeader("Content-Type", "text/plain");
            PrintWriter writer = response.getWriter();
            writer.print(msg);
            writer.close();
        } catch (IOException e) {
            LOG.warn("Failed to transmit error to client.", e);
        }
    }

    private class WSHandler extends WebSocketHandlerAdapter {
        @Override
        public void onOpen(WebSocket webSocket) throws IOException {

            AtmosphereResource atmosphereResource = webSocket.resource();

            String uuidParam = atmosphereResource.getRequest().getParameter("taskUuid");
            UUID taskUuid = DockerCloudUtils.tryParseAsUUID(uuidParam);

            if (taskUuid != null) {
                atmosphereResource.setBroadcaster(statusBroadcaster);
                statusBroadcaster.addAtmosphereResource(atmosphereResource);
                StatusListener listener = new StatusListener(atmosphereResource);
                try {
                    testMgr.setStatusListener(taskUuid, listener);
                } catch (IllegalArgumentException e) {
                    LOG.warn("Test was disposed, cannot register listener.");
                    listener.disposed();
                }
            }
        }

        @Override
        public void onError(WebSocket webSocket, WebSocketProcessor.WebSocketException t) {
            LOG.error("An error occurred while processing a request.");
        }
    }

    private class BuildServerListener extends BuildServerAdapter {
        @Override
        public void serverShutdown() {
            statusBroadcaster.destroy();
            atmosphereFramework.getBroadcasterFactory().remove(statusBroadcaster.getID());
            testMgr.dispose();
            super.serverShutdown();
        }

        @Override
        public void agentStatusChanged(@NotNull SBuildAgent agent, boolean wasEnabled, boolean wasAuthorized) {

            // We attempt here to disable the agent as soon as possible to prevent it from starting any job.
            UUID testInstanceUuid = DockerCloudUtils.tryParseAsUUID(DockerCloudUtils.getEnvParameter(agent,
                    DockerCloudUtils.ENV_TEST_INSTANCE_ID));

            if (testInstanceUuid != null) {
                agent.setEnabled(false, null, "Docker cloud test instance: should not accept any task.");
            }
        }
    }

    private class StatusListener implements ContainerTestStatusListener {

        final AtmosphereResource atmosphereResource;

        StatusListener(AtmosphereResource atmosphereResource) {
            this.atmosphereResource = atmosphereResource;
        }

        @Override
        public void notifyStatus(TestContainerStatusMsg statusMsg) {
            statusBroadcaster.broadcast(new XMLOutputter().outputString(statusMsg.toExternalForm()), atmosphereResource);
        }

        @Override
        public void disposed() {
            try {
                atmosphereResource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
