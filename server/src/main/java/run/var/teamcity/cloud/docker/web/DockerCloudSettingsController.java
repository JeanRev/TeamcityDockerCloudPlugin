package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Map;

public class DockerCloudSettingsController extends BaseController {

    public final static String EDIT_PATH = "docker-cloud-settings.html";

    @Nonnull
    private final PluginDescriptor pluginDescriptor;
    @Nonnull
    private final String jspPath;
    @Nonnull
    private final String htmlPath;

    public DockerCloudSettingsController(@Nonnull SBuildServer server,
                                         @Nonnull PluginDescriptor pluginDescriptor,
                                         @Nonnull WebControllerManager manager) {
        super(server);

        this.pluginDescriptor = pluginDescriptor;

        htmlPath = pluginDescriptor.getPluginResourcesPath(EDIT_PATH);
        jspPath = pluginDescriptor.getPluginResourcesPath("docker-cloud-settings.jsp");
        manager.registerController(htmlPath, this);

    }

    @Nullable
    @Override
    protected ModelAndView doHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response) throws Exception {
        ModelAndView mv = new ModelAndView(jspPath);
        Map<String, Object> model = mv.getModel();
        model.put("resPath", pluginDescriptor.getPluginResourcesPath());
        model.put("debugEnabled", DockerCloudUtils.isDebugEnabled());

        boolean defaultLocalInstanceAvailable;
        String defaultLocalInstanceParam;
        URI defaultLocalInstanceURI;
        boolean windowsHost;
        if (windowsHost = DockerCloudUtils.isWindowsHost()) {
            defaultLocalInstanceAvailable = DockerCloudUtils.isDefaultDockerNamedPipeAvailable();
            defaultLocalInstanceParam = DockerCloudUtils.USE_DEFAULT_WIN_NAMED_PIPE_PARAM;
            defaultLocalInstanceURI = DockerCloudUtils.DOCKER_DEFAULT_NAMED_PIPE_URI;
        } else {
            defaultLocalInstanceAvailable = DockerCloudUtils.isDefaultDockerSocketAvailable();
            defaultLocalInstanceParam = DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM;
            defaultLocalInstanceURI = DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI;
        }

        model.put("defaultLocalInstanceAvailable", defaultLocalInstanceAvailable);
        model.put("defaultLocalInstanceParam", defaultLocalInstanceParam);
        model.put("defaultLocalInstanceURI", defaultLocalInstanceURI);
        model.put("windowsHost", windowsHost);
        return mv;
    }
}
