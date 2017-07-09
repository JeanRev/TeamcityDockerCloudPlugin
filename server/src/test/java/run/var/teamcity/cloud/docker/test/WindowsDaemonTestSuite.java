package run.var.teamcity.cloud.docker.test;


import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Categories.class)
@Categories.IncludeCategory({WindowsDaemon.class})
@Suite.SuiteClasses(FullTestsSuite.class)
public class WindowsDaemonTestSuite {
    private WindowsDaemonTestSuite() {
        // Not instantiable.
    }
}
