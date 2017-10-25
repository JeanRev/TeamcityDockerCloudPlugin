package run.var.teamcity.cloud.docker;

/**
 * {@link DefaultDockerImageConfigParser} test suite.
 */
public class DefaultDockerImageConfigParserTest extends DockerImageConfigParserTest {

    @Override
    protected DockerImageConfigParser createParser() {
        return new DefaultDockerImageConfigParser();
    }
}