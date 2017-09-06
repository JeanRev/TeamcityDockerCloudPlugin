package run.var.teamcity.cloud.docker.test;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Categories.class)
@Categories.ExcludeCategory({ Integration.class, LongRunning.class, WindowsDaemon.class })
@Suite.SuiteClasses(FullTestsSuite.class)
public class QuickTestSuite {
    private QuickTestSuite(){
        // Not instantiable.
    }
}
