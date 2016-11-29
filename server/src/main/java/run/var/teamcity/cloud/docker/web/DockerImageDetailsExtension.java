package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.clouds.web.CloudImageDetailsExtensionBase;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.DockerImage;

/**
 * {@link CloudImageDetailsExtensionBase} for {@link DockerImage}s.
 */
public class DockerImageDetailsExtension extends CloudImageDetailsExtensionBase<DockerImage> {
    public DockerImageDetailsExtension(@NotNull PagePlaces pagePlaces, @NotNull PluginDescriptor descr) {
        super(DockerImage.class, pagePlaces, descr, "docker-cloud-image-details.jsp");
    }
}
