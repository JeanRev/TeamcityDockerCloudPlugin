package run.var.teamcity.cloud.docker.web;

import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Test
public class WebUtilsTest {

    public void normalOperation() {
        // Not much expected here.
        WebUtils.configureRequestForAtmosphere(new TestHttpServletRequest());
    }

    public void invalidInput() {
        //noinspection ConstantConditions
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                () -> WebUtils.configureRequestForAtmosphere(null));
    }
}