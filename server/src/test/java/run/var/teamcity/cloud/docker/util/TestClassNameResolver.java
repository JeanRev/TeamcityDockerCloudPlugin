package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class TestClassNameResolver extends ClassNameResolver {

    private final Set<String> knownClasses = new HashSet<>();
    private ClassLoader queriedClassLoader;

    @Override
    public synchronized boolean isInClassLoader(@Nonnull String cls, @Nonnull ClassLoader classLoader) {
        this.queriedClassLoader = classLoader;
        return knownClasses.contains(cls);
    }

    public synchronized ClassLoader getQueriedClassLoader() {
        return queriedClassLoader;
    }

    public synchronized TestClassNameResolver knownClass(String cls) {
        knownClasses.add(cls);
        return this;
    }
}
