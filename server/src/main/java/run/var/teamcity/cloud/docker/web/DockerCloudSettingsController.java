package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by jr on 10.05.16.
 */
public class DockerCloudSettingsController extends BaseController {

    public final static String EDIT_PATH = "docker-cloud-settings.html";

    @NotNull private final PluginDescriptor pluginDescriptor;
    @NotNull private final String jspPath;
    @NotNull private final String htmlPath;

    public DockerCloudSettingsController(@NotNull SBuildServer server,
                                         @NotNull PluginDescriptor pluginDescriptor,
                                         @NotNull WebControllerManager manager) {
        super(server);

        this.pluginDescriptor = pluginDescriptor;

        htmlPath = pluginDescriptor.getPluginResourcesPath(EDIT_PATH);
        jspPath = pluginDescriptor.getPluginResourcesPath("docker-cloud-settings.jsp");
        manager.registerController(htmlPath, this);

    }

    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        ModelAndView mv = new ModelAndView(jspPath);
        mv.getModel().put("resPath", pluginDescriptor.getPluginResourcesPath());
        return mv;
    }
}
