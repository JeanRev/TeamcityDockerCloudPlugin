package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;

class DefaultClassNameResolver extends ClassNameResolver {
    @Override
    public boolean isInClassLoader(@Nonnull String cls, @Nonnull ClassLoader classLoader) {
        DockerCloudUtils.requireNonNull(cls, "Class name cannot be null.");
        DockerCloudUtils.requireNonNull(classLoader, "Class-loader cannot be null.");
        try {
            classLoader.loadClass(cls);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
