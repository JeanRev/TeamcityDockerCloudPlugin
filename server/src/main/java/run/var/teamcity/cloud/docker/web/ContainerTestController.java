package run.var.teamcity.cloud.docker.web;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.atmosphere.cpr.*;
import org.atmosphere.util.SimpleBroadcaster;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandlerAdapter;
import org.atmosphere.websocket.WebSocketProcessor;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerCloudClientConfigException;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.client.DockerRegistryClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.web.atmo.DefaultAtmosphereFacade;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Spring controller to manage lifecycle of container tests.
 */
public class ContainerTestController extends BaseFormXmlController {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestController.class);

    enum Action {
        CREATE,
        START,
        QUERY,
        CANCEL,
        LOGS,
    }

    public static final String PATH = "test-container.html";

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, TestListener> listeners = new HashMap<>();
    private final ContainerTestManager testMgr;
    private final AtmosphereFrameworkFacade atmosphereFramework;
    private final Broadcaster statusBroadcaster;
    private final DockerClientFactory dockerClientFactory;

    @Autowired
    public ContainerTestController(@Nonnull DefaultAtmosphereFacade atmosphereFramework,
                                   @Nonnull SBuildServer server,
                                   @Nonnull PluginDescriptor pluginDescriptor,
                                   @Nonnull WebControllerManager manager,
                                   @Nonnull WebLinks webLinks,
                                   @Nonnull StreamingController streamingController) {
        this(DockerClientFactory.getDefault(), atmosphereFramework, server, pluginDescriptor, manager,
                new DefaultContainerTestManager(OfficialAgentImageResolver.forCurrentServer(DockerRegistryClientFactory.getDefault()),
                        DockerClientFactory.getDefault(), server, webLinks, streamingController));

    }

    ContainerTestController(@Nonnull DockerClientFactory dockerClientFactory,
                            @Nonnull AtmosphereFrameworkFacade atmosphereFramework,
                            @Nonnull SBuildServer buildServer,
                            @Nonnull PluginDescriptor pluginDescriptor,
                            @Nonnull WebControllerManager manager,
                            @Nonnull ContainerTestManager testMgr) {


        this.dockerClientFactory = dockerClientFactory;
        this.testMgr = testMgr;

        buildServer.addListener(new BuildServerListener());
        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
        manager.registerController("/app/docker-cloud/test-container/**", this);

        atmosphereFramework.addWebSocketHandler("/app/docker-cloud/test-container/getStatus", new WSHandler(),
                AtmosphereFramework.REFLECTOR_ATMOSPHEREHANDLER, Collections.emptyList());

        statusBroadcaster = atmosphereFramework.getBroadcasterFactory().get(SimpleBroadcaster.class, UUID.randomUUID());

        this.atmosphereFramework = atmosphereFramework;
    }


    @Override
    protected ModelAndView doGet(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response) {

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
    protected void doPost(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Element xmlResponse) {

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


        if (action == Action.CREATE) {
            Map<String, String> params = DockerCloudUtils.extractTCPluginParams(request);

            DockerCloudClientConfig clientConfig;
            DockerImageConfig imageConfig;

            try {
                clientConfig = DockerCloudClientConfig.processParams(params, dockerClientFactory);
                // Note: we let the cloud image parameters here to "null" because the test container will actually not
                // be started through the cloud API.
                imageConfig = DockerImageConfig.fromJSon(Node.parse(params.get(DockerCloudUtils.TEST_IMAGE_PARAM)), null);
            } catch (DockerCloudClientConfigException e) {
                sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid configuration. Please check your connection settings.");
                return;
            } catch (Exception e) {
                LOG.error("Data parsing failed.", e);
                sendErrorQuietly(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to parse data.");
                return;
            }

            TestListener listener = new TestListener();
            UUID testUuid = testMgr.createNewTestContainer(clientConfig, imageConfig, listener);
            lock.lock();
            try {
                listeners.put(testUuid, listener);
            } finally {
                lock.unlock();
            }

            xmlResponse.addContent(new Element("testUuid").setText(testUuid.toString()));
            return;
        }

        String uuidParam = request.getParameter("testUuid");
        UUID testUuid = DockerCloudUtils.tryParseAsUUID(uuidParam);
        TestListener listener = listeners.get(testUuid);
        if (testUuid == null || listener == null) {
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Bad request identifier: " + uuidParam);
            return;
        }

        if (action == Action.START) {
            testMgr.startTestContainer(testUuid);
            return;
        }

        if (action == Action.QUERY) {
            TestContainerStatusMsg lastStatus = listener.lastStatus;
            if (lastStatus != null) {
                xmlResponse.addContent(listener.lastStatus.toExternalForm());
            }
            testMgr.notifyInteraction(testUuid);
            return;
        }

        if (action == Action.LOGS) {
            xmlResponse.addContent(new Element("logs").setText(DockerCloudUtils.filterXmlText(testMgr.getLogs(testUuid))));
            return;
        }

        assert action == Action.CANCEL : "Unknown enum member: " + action;

        try {
            testMgr.dispose(testUuid);
        } catch (Exception e) {
            // Ignore.
        }
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

            String uuidParam = atmosphereResource.getRequest().getParameter("testUuid");
            TestListener listener = null;
            UUID testUuid = DockerCloudUtils.tryParseAsUUID(uuidParam);
            if (testUuid != null) {
                listener = listeners.get(testUuid);
            }
            if (listener == null) {
                webSocket.writeError(atmosphereResource.getResponse(), HttpServletResponse.SC_BAD_REQUEST,
                        "Bad or expired request");
                webSocket.close();
                return;
            }

            atmosphereResource.setBroadcaster(statusBroadcaster);
            statusBroadcaster.addAtmosphereResource(atmosphereResource);
            listener.setAtmosphereResource(atmosphereResource);
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
    }

    private class TestListener implements ContainerTestListener {

        volatile TestContainerStatusMsg lastStatus;
        volatile AtmosphereResource atmosphereResource;

        @Override
        public void notifyStatus(@Nullable TestContainerStatusMsg statusMsg) {
            lastStatus = statusMsg;
            broadcast(statusMsg);
        }

        void setAtmosphereResource(AtmosphereResource atmosphereResource) {
            this.atmosphereResource = atmosphereResource;
            // Important: broadcast the current status as soon as a the WebSocket resource is registered (may happens some
            // time after the test action was invoked).
            broadcast(lastStatus);
        }

        private void broadcast(TestContainerStatusMsg statusMsg) {
            if (atmosphereResource != null && statusMsg != null) {
                statusBroadcaster.broadcast(new XMLOutputter().outputString(statusMsg.toExternalForm()), atmosphereResource);
            }
        }

        @Override
        public void disposed() {
            AtmosphereResource atmosphereResource = this.atmosphereResource;
            if (atmosphereResource != null) {
                try {
                    atmosphereResource.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }

            lock.lock();
            try {
                listeners.values().remove(this);
            } finally {
                lock.unlock();
            }
        }
    }
}
