package run.var.teamcity.cloud.docker;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ContainerInspection} test suite.
 */
public class ContainerInspectionTest {

    @Test
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new ContainerInspection(null));
    }

    @Test
    public void getName() {
        ContainerInspection inspection = new ContainerInspection("container_name");
        assertThat(inspection.getName()).isEqualTo("container_name");
    }

}