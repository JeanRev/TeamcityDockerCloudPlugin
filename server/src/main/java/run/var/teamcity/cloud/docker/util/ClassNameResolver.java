package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;

/**
 * Simple utility to check a class presence in a given classloader.
 * <p>
 *     Implemented as abstract class to enable testing.
 * </p>
 */
public abstract class ClassNameResolver {

    /**
     * Verifies if a class can be loaded from the given class-loader.
     *
     * @param cls the class name
     * @param classLoader the class loader
     *
     * @return {@code true} if the class can be loaded, {@code false} otherwise
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public abstract boolean isInClassLoader(@Nonnull String cls, @Nonnull ClassLoader classLoader);

    /**
     * Gets the default class name resolver.
     *
     * @return the default class name resolver
     */
    @Nonnull
    public static ClassNameResolver getDefault() {
        return new DefaultClassNameResolver();
    }
}
