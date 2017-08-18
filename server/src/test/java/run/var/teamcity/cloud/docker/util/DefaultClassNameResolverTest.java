package run.var.teamcity.cloud.docker.util;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestClassLoader;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link DefaultClassNameResolver} test suite.
 */
public class DefaultClassNameResolverTest {

    @Test
    public void shouldUseProvidedClassloader() {
        TestClassLoader cl = new TestClassLoader();

        ClassNameResolver.getDefault().isInClassLoader("some.non.existing.class", cl);

        assertThat(cl.getQueriedClass()).isEqualTo("some.non.existing.class");
    }

    @Test
    public void isInClassLoaderReturnsFalseWhenClassNotFound() {
        TestClassLoader cl = new TestClassLoader();

        ClassNameResolver resolver = ClassNameResolver.getDefault();;

        assertThat(resolver.isInClassLoader("some.non.existing.class", cl)).isFalse();
    }

    @Test
    public void isInClassLoaderReturnsTrueWhenClassFound() {
        TestClassLoader cl = new TestClassLoader();

        ClassNameResolver resolver = ClassNameResolver.getDefault();;

        assertThat(resolver.isInClassLoader(DockerCloudUtils.class.getName(), cl)).isTrue();
    }
}