package run.var.teamcity.cloud.docker.web;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.SessionUser;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerCloudClientConfigException;
import run.var.teamcity.cloud.docker.DockerCloudSupportRegistry;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring controller to manage lifecycle of container tests.
 */
public class ContainerTestController extends BaseFormJsonController {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestController.class);

    enum Action {
        CREATE,
        START,
        QUERY,
        CANCEL,
        LOGS,
    }

    public static final String PATH = "test-container.html";

    private final AgentHolderTestManager testMgr;
    private final DockerCloudSupportRegistry cloudSupportRegistry;


    ContainerTestController(@Nonnull DockerCloudSupportRegistry cloudSupportRegistry,
                            @Nonnull SBuildServer buildServer,
                            @Nonnull PluginDescriptor pluginDescriptor,
                            @Nonnull WebControllerManager manager,
                            @Nonnull AgentHolderTestManager testMgr) {
        this.cloudSupportRegistry = cloudSupportRegistry;
        this.testMgr = testMgr;
        buildServer.addListener(new BuildServerListener());
        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
        manager.registerController("/app/docker-cloud/test-container", this);
    }


    @Override
    protected ModelAndView doGet(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response) {
        return null;
    }

    @Override
    protected void doPost(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull
            EditableNode responseNode) {

        if (!WebUtils.isAuthorizedToRunContainerTests(SessionUser.getUser(request))) {
            sendErrorQuietly(response, HttpServletResponse.SC_UNAUTHORIZED, "Bad or missing user session");
            return;
        }

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
                clientConfig = DockerCloudClientConfig.processParams(params, cloudSupportRegistry);
                imageConfig = clientConfig.getCloudSupport().createImageConfigParser().
                        fromJSon(Node.parse(params.get(DockerCloudUtils.TEST_IMAGE_PARAM)),
                                Collections.emptyList());
            } catch (DockerCloudClientConfigException e) {
                LOG.error("Invalid cloud client configuration.", e);
                sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid configuration. Please check your connection settings.");
                return;
            } catch (Exception e) {
                LOG.error("Data parsing failed.", e);
                sendErrorQuietly(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to parse data.");
                return;
            }

            UUID testUuid = testMgr.createNewTestContainer(clientConfig, imageConfig);

            ContainerTestReference testRef = ContainerTestReference.newTestReference(clientConfig.getCloudSupport(),
                    testUuid, clientConfig.getDockerClientConfig());

            HttpSession httpSession = request.getSession();
            testRef.persistInHttpSession(httpSession);

            testMgr.setListener(testUuid, new AgentHolderTestListener() {
                @Override
                public void notifyStatus(@Nonnull TestAgentHolderStatusMsg statusMsg) {
                    // Nothing to do.
                }

                @Override
                public void disposed() {
                    testRef.clearFromHttpSession(httpSession);
                }
            });

            responseNode.put("testUuid", testUuid);
            return;
        }

        String uuidParam = request.getParameter("testUuid");
        UUID testUuid = DockerCloudUtils.tryParseAsUUID(uuidParam);

        if (testUuid == null) {
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Bad request identifier: " + uuidParam);
            return;
        }

        Optional<ContainerTestReference> testRefOpt = ContainerTestReference.retrieveFromHttpSession(
                request.getSession(), testUuid);

        if (!testRefOpt.isPresent()) {
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Bad or expired test uuid: " + testUuid);
            return;
        }

        ContainerTestReference testRef = testRefOpt.get();

        if (action == Action.START) {
            testMgr.startTestContainer(testUuid);
            return;
        }

        if (action == Action.QUERY) {
            Optional<TestAgentHolderStatusMsg> lastStatus = testMgr.retrieveStatus(testUuid);
            if (lastStatus.isPresent()) {
                TestAgentHolderStatusMsg statusMsg = lastStatus.get();
                responseNode.put("statusMsg", statusMsg.toExternalForm());
                Optional<String> agentHolderId = statusMsg.getAgentHolderId();
                if (agentHolderId.isPresent() && !testRef.getContainerId().isPresent()) {
                    testRef.registerContainer(agentHolderId.get()).persistInHttpSession(request.getSession());
                }
            }
            return;
        }

        if (action == Action.LOGS) {
            responseNode.put("logs", testMgr.getLogs(testUuid));
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

    private class BuildServerListener extends BuildServerAdapter {
        @Override
        public void serverShutdown() {
            testMgr.dispose();
            super.serverShutdown();
        }
    }


}
