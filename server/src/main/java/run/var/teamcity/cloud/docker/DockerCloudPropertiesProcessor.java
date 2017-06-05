package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Docker {@link PropertiesProcessor}. Delegates the processing to the configuration class
 * {@link DockerCloudClientConfig}.
 */
class DockerCloudPropertiesProcessor implements PropertiesProcessor {

    @Override
    public Collection<InvalidProperty> process(Map<String, String> properties) {
        List<InvalidProperty> invalidProperties = new ArrayList<>();
        try {
            DockerCloudClientConfig.processParams(properties, DockerClientAdapterFactory.getDefault());
        } catch (DockerCloudClientConfigException e) {
            invalidProperties.addAll(e.getInvalidProperties());
        }
        try {
            DockerImageConfig.processParams(properties);
        } catch (DockerCloudClientConfigException e) {
            invalidProperties.addAll(e.getInvalidProperties());
        }
        return invalidProperties;
    }
}
