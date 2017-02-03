package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.clouds.web.CloudImageDetailsExtensionBase;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import run.var.teamcity.cloud.docker.DockerImage;

import javax.annotation.Nonnull;

/**
 * {@link CloudImageDetailsExtensionBase} for {@link DockerImage}s.
 */
public class DockerImageDetailsExtension extends CloudImageDetailsExtensionBase<DockerImage> {
    public DockerImageDetailsExtension(@Nonnull PagePlaces pagePlaces, @Nonnull PluginDescriptor descr) {
        super(DockerImage.class, pagePlaces, descr, "docker-cloud-image-details.jsp");
    }
}
