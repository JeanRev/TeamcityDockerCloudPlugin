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
    private final boolean ignoreMissingResources;

    public TestResourceBundle() {
        this(false);
    }
    public TestResourceBundle(boolean ignoreMissingResources) {
        this.ignoreMissingResources = ignoreMissingResources;
    }

    @Override
    protected Object handleGetObject(@NotNull String key) {
        return resourcesMap.getOrDefault(key, ignoreMissingResources ? key : null);
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
