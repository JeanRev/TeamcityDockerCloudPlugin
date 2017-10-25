package run.var.teamcity.cloud.docker;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.Resources;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public class TestResourceBundle extends ResourceBundle {

    private Map<String, String> resourcesMap = new ConcurrentHashMap<>();

    public TestResourceBundle() {
        ResourceBundle bundle = ResourceBundle.getBundle("run.var.teamcity.cloud.docker.testResources");
        bundle.keySet().forEach(key -> resourcesMap.put(key, bundle.getString(key)));
    }

    @Override
    protected Object handleGetObject(@NotNull String key) {
        return resourcesMap.get(key);
    }

    @NotNull
    @Override
    public Enumeration<String> getKeys() {
        return Collections.enumeration(resourcesMap.keySet());
    }

    public Map<String, String> getResourcesMap() {
        return resourcesMap;
    }
}
