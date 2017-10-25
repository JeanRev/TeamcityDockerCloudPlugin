package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudImageParameters;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * Parser for a {@link DockerImageConfig} stored in the JSON format.
 */
public interface DockerImageConfigParser {

    /**
     * Load an image configuration from a JSON node.
     *
     * @param node the JSON node
     * @param imagesParameters images parameters provided from the Cloud API if any
     *
     * @return the loaded configuration
     *
     * @throws NullPointerException     if {@code node} is {@code null}
     * @throws IllegalArgumentException if no valid configuration could be build from the provided JSON node
     */
    @Nonnull
    DockerImageConfig fromJSon(@Nonnull Node node, @Nonnull Collection<CloudImageParameters> imagesParameters);
}
