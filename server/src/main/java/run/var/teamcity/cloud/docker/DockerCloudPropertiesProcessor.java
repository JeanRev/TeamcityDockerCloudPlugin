package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.client.DockerClientProcessingException;

import java.util.*;

/**
 * Docker {@link PropertiesProcessor}. Delegates the processing to the configuration class
 * {@link DockerCloudClientConfig}.
 */
class DockerCloudPropertiesProcessor implements PropertiesProcessor {

    @Override
    public Collection<InvalidProperty> process(Map<String, String> properties) {
        List<InvalidProperty> invalidProperties = new ArrayList<>();
        try {
            DockerCloudClientConfig.processParams(properties, DockerClientFactory.getDefault());
        } catch (DockerCloudClientConfigException e) {
            invalidProperties.addAll(e.getInvalidProperties());
        }
        try {
            DockerImageConfig.processParams(properties);
        } catch (DockerCloudClientConfigException e) {
            invalidProperties.addAll(e.getInvalidProperties());
        }
        return Collections.emptyList();
    }
}
