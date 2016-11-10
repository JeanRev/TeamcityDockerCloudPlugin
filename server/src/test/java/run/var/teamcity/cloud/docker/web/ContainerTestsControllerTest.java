package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.WebLinks;
import org.jdom.Element;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.test.TestAtmosphereFrameworkFacade;
import run.var.teamcity.cloud.docker.test.TestBuildAgentManager;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;
import run.var.teamcity.cloud.docker.test.TestHttpServletResponse;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestRootUrlHolder;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestWebControllerManager;

/**
 * {@link ContainerTestsController} test suite.
 */
@Test
public class ContainerTestsControllerTest {

    public void fullTest() {
        ContainerTestsController ctrl = new ContainerTestsController(new TestAtmosphereFrameworkFacade(),
                new TestSBuildServer(), new TestPluginDescriptor(), new TestWebControllerManager(),
                new TestBuildAgentManager(), new WebLinks(new TestRootUrlHolder()));

        ctrl.doPost(new TestHttpServletRequest(), new TestHttpServletResponse(), new Element("root"));
    }
}