package run.var.teamcity.cloud.docker.web;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;
import run.var.teamcity.cloud.docker.test.TestHttpServletResponse;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestWebControllerManager;

public class DockerCloudSettingsControllerTest {

    @Test
    public void normalOperation() throws Exception {
        DockerCloudSettingsController ctrl = createController();
        ctrl.doHandle(new TestHttpServletRequest(), new TestHttpServletResponse());
    }

    private DockerCloudSettingsController createController() {
        return new DockerCloudSettingsController(new TestSBuildServer(), new TestPluginDescriptor(), new
                TestWebControllerManager());
    }
}