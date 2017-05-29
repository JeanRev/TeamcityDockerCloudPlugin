package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Spring controller to handle Docker connectivity tests.
 */
public class CheckConnectivityController extends BaseFormJsonController {

    public static final String PATH = "checkconnectivity.html";

    private final DockerClientFactory dockerClientFactory;


    public CheckConnectivityController(@Nonnull SBuildServer server,
                                       @Nonnull PluginDescriptor pluginDescriptor,
                                       @Nonnull WebControllerManager manager) {
        this(server, pluginDescriptor, manager, DockerClientFactory.getDefault());
    }

    CheckConnectivityController(@Nonnull SBuildServer server,
                                @Nonnull PluginDescriptor pluginDescriptor,
                                @Nonnull WebControllerManager manager,
                                @Nonnull DockerClientFactory dockerClientFactory) {
        this.dockerClientFactory = dockerClientFactory;
        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
    }

    @Override
    protected ModelAndView doGet(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response) {
        // Nothing to do.
        return null;
    }

    @Override
    protected void doPost(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull EditableNode responseNode) {

        Map<String, String> properties = DockerCloudUtils.extractTCPluginParams(request);

        String uri = properties.get(DockerCloudUtils.INSTANCE_URI);
        boolean useTLS = Boolean.parseBoolean(properties.get(DockerCloudUtils.USE_TLS));

        Exception error = null;
        try {

            DockerClientConfig dockerConfig = new DockerClientConfig(new URI(uri),
                    DockerCloudUtils.DOCKER_API_TARGET_VERSION)
                    .usingTls(useTLS)
                    .connectionPoolSize(1)
                    .connectTimeoutMillis((int) TimeUnit.SECONDS.toMillis(20));

            DockerClient client = dockerClientFactory.createClientWithAPINegotiation(dockerConfig);

            DockerAPIVersion effectiveApiVersion = client.getApiVersion();

            Node version = client.getVersion();

            responseNode.put("info", version);
            responseNode.getOrCreateObject("meta")
                    .put("serverTime", System.currentTimeMillis())
                    .put("effectiveApiVersion", effectiveApiVersion.getVersionString());
        } catch (Exception e) {
            error = e;
        }

        if (error != null) {
            String msg = error.getMessage();
            responseNode
                    .put("error", msg != null ? msg : "")
                    .put("failureCause", DockerCloudUtils.getStackTrace(error));
        }
    }

    private static void setAttr(@Nonnull Element elt, @Nonnull String name, @Nullable Object value) {
        if (value != null) {
            elt.setAttribute(name, value.toString());
        }
    }
}
