package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Docker {@link PropertiesProcessor}. Delegates the processing to the configuration class
 * {@link DockerCloudClientConfig}.
 */
class DockerCloudPropertiesProcessor implements PropertiesProcessor {

    private final DockerCloudSupportRegistry cloudSupportRegistry;
    private final DockerImageConfigParser imageConfigParser;

    DockerCloudPropertiesProcessor(DockerCloudSupportRegistry cloudSupportRegistry, @Nonnull DockerImageConfigParser
            imageConfigParser) {
        this.cloudSupportRegistry = DockerCloudUtils.requireNonNull(cloudSupportRegistry, "Cloud support registry " +
                "cannot be null.");
        this.imageConfigParser = DockerCloudUtils.
                requireNonNull(imageConfigParser, "Image config parser cannot be null.");
    }

    @Override
    public Collection<InvalidProperty> process(Map<String, String> properties) {
        List<InvalidProperty> invalidProperties = new ArrayList<>();
        try {
            DockerCloudClientConfig.processParams(properties, cloudSupportRegistry);
        } catch (DockerCloudClientConfigException e) {
            invalidProperties.addAll(e.getInvalidProperties());
        }
        try {
            DockerImageConfig.processParams(imageConfigParser, properties);
        } catch (DockerCloudClientConfigException e) {
            invalidProperties.addAll(e.getInvalidProperties());
        }
        return invalidProperties;
    }
}
