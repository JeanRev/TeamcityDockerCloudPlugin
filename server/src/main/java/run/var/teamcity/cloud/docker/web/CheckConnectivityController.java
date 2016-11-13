package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Map;


public class CheckConnectivityController extends BaseFormXmlController {

    public static final String PATH = "dockerCloud-checkconnectivity.html";

    private final DockerClientFactory dockerClientFactory;


    public CheckConnectivityController(@NotNull SBuildServer server,
                                       @NotNull PluginDescriptor pluginDescriptor,
                                       @NotNull WebControllerManager manager) {
        this(server, pluginDescriptor, manager, DockerClientFactory.getDefault());
    }

    CheckConnectivityController(@NotNull SBuildServer server,
                                @NotNull PluginDescriptor pluginDescriptor,
                                @NotNull WebControllerManager manager,
                                @NotNull DockerClientFactory dockerClientFactory) {
        super(server);
        this.dockerClientFactory = dockerClientFactory;
        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
    }

    @Override
    protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        // Nothing to do.
        return null;
    }

    @Override
        protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {


        Map<String, String> properties = DockerCloudUtils.extractTCPluginParams(request);

        String uri = properties.get(DockerCloudUtils.INSTANCE_URI);
        boolean useTLS = Boolean.parseBoolean(properties.get(DockerCloudUtils.USE_TLS));

        String errorMsg = null;
        try {

            DockerClientConfig dockerConfig = new DockerClientConfig(new URI(uri)).usingTls(useTLS).threadPoolSize(1);

            DockerClient client = dockerClientFactory.createClient(dockerConfig);

            Node version = client.getVersion();
            Element versionElt = new Element("version");
            setAttr(versionElt, "docker", version.getAsString("Version", null));
            setAttr(versionElt, "api", version.getAsString("ApiVersion", null));
            setAttr(versionElt, "os", version.getAsString("Os", null));
            setAttr(versionElt, "arch", version.getAsString("Arch", null));
            setAttr(versionElt, "kernel", version.getAsString("KernelVersion", null));
            setAttr(versionElt, "build", version.getAsString("GitCommit", null));
            setAttr(versionElt, "buildTime", version.getAsString("BuildTime", null));
            setAttr(versionElt, "go", version.getAsString("GoVersion", null));
            setAttr(versionElt, "experimental", version.getAsBoolean("experimental", false));
            xmlResponse.addContent(versionElt);
        } catch (Exception e) {
            errorMsg = e.getMessage();
        }

        if (errorMsg != null) {
            Element errorElt = new Element("error");
            errorElt.setText(errorMsg);
            xmlResponse.addContent(errorElt);
        }
    }

    private static void setAttr(@NotNull Element elt, @NotNull String name, @Nullable Object value) {
        if (value != null) {
            elt.setAttribute(name, value.toString());
        }
    }
}
