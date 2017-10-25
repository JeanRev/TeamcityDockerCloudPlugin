package run.var.teamcity.cloud.docker;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

public class DefaultDockerCloudSupportRegistryTest {

    @Test
    public void normalLookup() {
        DefaultDockerCloudSupportRegistry registry = new DefaultDockerCloudSupportRegistry();
        for (DefaultDockerCloudSupport support : DefaultDockerCloudSupport.values()) {
            assertThat(registry.getSupport(support.code())).isEqualTo(support);
        }
    }

    @Test
    public void invalidInput() {
        DefaultDockerCloudSupportRegistry registry = new DefaultDockerCloudSupportRegistry();
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> registry.getSupport(null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> registry.getSupport("unknown code"));
    }
}