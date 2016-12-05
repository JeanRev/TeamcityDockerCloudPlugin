package run.var.teamcity.cloud.docker.test;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Categories.class)
@Categories.ExcludeCategory({ LongRunning.class, Integration.class })
@Suite.SuiteClasses(AllTestsSuite.class)
public class QuickTestSuite {
}
