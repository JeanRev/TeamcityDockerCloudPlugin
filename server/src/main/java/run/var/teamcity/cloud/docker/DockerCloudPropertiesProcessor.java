package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Docker {@link PropertiesProcessor}. Delegates the processing to the configuration class
 * {@link DockerCloudClientConfig}.
 */
class DockerCloudPropertiesProcessor implements PropertiesProcessor {

    @Override
    public Collection<InvalidProperty> process(Map<String, String> properties) {
        try {
            DockerCloudClientConfig.processParams(properties);
        } catch (DockerCloudClientConfigException e) {
            return e.getInvalidProperties();
        }
        return Collections.emptyList();
    }
}
