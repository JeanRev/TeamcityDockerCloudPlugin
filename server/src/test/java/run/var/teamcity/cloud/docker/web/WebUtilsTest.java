package run.var.teamcity.cloud.docker.web;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link WebUtils} test suite.
 */
public class WebUtilsTest {

    @Test
    public void normalOperation() {
        // Not much expected here.
        WebUtils.configureRequestForAtmosphere(new TestHttpServletRequest());
    }

    @Test
    public void invalidInput() {
        //noinspection ConstantConditions
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                () -> WebUtils.configureRequestForAtmosphere(null));
    }
}