package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Map;


public class DockerCloudCheckConnectivityController extends BaseFormXmlController {

    public static final String PATH = "dockerCloud-checkconnectivity.html";

    public DockerCloudCheckConnectivityController(@NotNull SBuildServer server,
                                                  @NotNull PluginDescriptor pluginDescriptor,
                                                  @NotNull WebControllerManager manager) {
        super(server);

        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
    }

    @Override
    protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        // Nothing to do.
        return null;
    }

    @Override
    protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
        BasePropertiesBean propsBean = new BasePropertiesBean(null);
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

        Map<String, String> properties = propsBean.getProperties();
        String uri = properties.get(DockerCloudUtils.INSTANCE_URI);
        boolean useTLS = Boolean.parseBoolean(properties.get(DockerCloudUtils.USE_TLS));

        String errorMsg = null;
        try {

            DockerClient client = DockerClient.open(new URI(uri), useTLS, 1);

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

        /*
        StringBuilder versionStr = new StringBuilder(DockerVersion.fromString(version.getApiVersion()).toString());
        String build = version.getGitCommit();
        if (build != null) {
            versionStr.append(" build ").append(build);
        }
        String os = version.getOperatingSystem();
        if (os != null) {
            versionStr.append(" on ").append(os);
            String arch = version.getArch();
            if (arch != null) {
                versionStr.append("/").append(arch);
            }
        }

        versionStr.append(" ").append("");
        */

    }

    private static void setAttr(@NotNull Element elt, @NotNull String name, @Nullable Object value) {
        if (value != null) {
            elt.setAttribute(name, value.toString());
        }
    }
}
