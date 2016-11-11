package run.var.teamcity.cloud.docker.web;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.jdom.Element;
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
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

    @Autowired
    public ContainerTestController(@NotNull DefaultAtmosphereFacade atmosphereFramework,
                                   @NotNull SBuildServer server,
                                   @NotNull PluginDescriptor pluginDescriptor,
                                   @NotNull WebControllerManager manager,
                                   @NotNull WebLinks webLinks) {
        this(atmosphereFramework, pluginDescriptor, manager, new DefaultContainerTestManager(atmosphereFramework,
                        OfficialAgentImageResolver.forServer(server), DockerClientFactory.getDefault(),
                        server, webLinks));


    }

    ContainerTestController(@NotNull AtmosphereFrameworkFacade atmosphereFramework,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @NotNull WebControllerManager manager,
                            @NotNull ContainerTestManager testMgr) {


        this.testMgr = testMgr;


        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
        manager.registerController("/app/docker-cloud/test-container/**", this);

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

            clientConfig = DockerCloudClientConfig.processParams(params);
            try {
                imageConfig = DockerImageConfig.fromJSon(Node.parse(params.get(DockerCloudUtils.TEST_IMAGE_PARAM)));
            } catch (IOException e) {
                LOG.error("Image parsing failed.", e);
                sendErrorQuietly(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to parse image data.");
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
}
