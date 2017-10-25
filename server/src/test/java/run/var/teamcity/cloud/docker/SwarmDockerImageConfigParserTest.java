package run.var.teamcity.cloud.docker;


public class SwarmDockerImageConfigParserTest extends DockerImageConfigParserTest {

    @Override
    protected DockerImageConfigParser createParser() {
        return new SwarmDockerImageConfigParser();
    }
}