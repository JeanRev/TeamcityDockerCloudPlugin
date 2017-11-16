package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestURLBuilder;
import run.var.teamcity.cloud.docker.util.Node;

import java.net.URL;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link DefaultDockerImageMigrationHandler} test suite.
 */
public class DefaultDockerImageMigrationHandlerTest {

    private static final Node TEST_NODE = Node.EMPTY_OBJECT.editNode().put("foo", "bar").saveNode();

    @Test
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new DefaultDockerImageMigrationHandler
                (null));
    }

    @Test
    public void noopMigrationScript() {
        URL url = TestURLBuilder.forContent(noopScript()).build();

        DefaultDockerImageMigrationHandler migrationHandler = new DefaultDockerImageMigrationHandler(url);

        Node result = migrationHandler.performMigration(TEST_NODE);

        assertThat(result).isEqualTo(TEST_NODE);
    }

    @Test
    public void effectiveMigration() {
        URL url = TestURLBuilder.forContent(script("input.foo = 'baz';")).build();

        DefaultDockerImageMigrationHandler migrationHandler = new DefaultDockerImageMigrationHandler(url);

        Node result = migrationHandler.performMigration(TEST_NODE);

        assertThat(result).isEqualTo(TEST_NODE.editNode().put("foo", "baz"));
    }

    @Test
    public void globalStateMustBeCleared() {
        URL url = TestURLBuilder.forContent("var globalVar = 0;" +
                script("input.cpt = globalVar; globalVar++;")).build();

        DefaultDockerImageMigrationHandler migrationHandler = new DefaultDockerImageMigrationHandler(url);

        Node result = migrationHandler.performMigration(TEST_NODE);

        assertThat(result).isEqualTo(TEST_NODE.editNode().put("cpt", 0));

        result = migrationHandler.performMigration(TEST_NODE);

        assertThat(result).isEqualTo(TEST_NODE.editNode().put("cpt", 0));
    }

    @Test
    public void mustHandleJSONEscapeCharacters() {
        URL url = TestURLBuilder.forContent(noopScript()).build();

        DefaultDockerImageMigrationHandler migrationHandler = new DefaultDockerImageMigrationHandler(url);

        Node testNode = Node.EMPTY_OBJECT.editNode().put("foo", "{\"\':").saveNode();

        Node result = migrationHandler.performMigration(testNode);

        assertThat(result).isEqualTo(testNode);
    }

    @Test
    public void mustHandleMultiByteCharSequences() {
        URL url = TestURLBuilder.forContent(noopScript()).build();

        DefaultDockerImageMigrationHandler migrationHandler = new DefaultDockerImageMigrationHandler(url);

        Node testNode = Node.EMPTY_OBJECT.editNode().put("foo", "😀").saveNode();

        Node result = migrationHandler.performMigration(testNode);

        assertThat(result).isEqualTo(testNode);
    }

    @Test
    public void invalidScriptMustThrowException() {
        URL url = TestURLBuilder.forContent(script("throw 'invalid script';")).build();
        DefaultDockerImageMigrationHandler migrationHandler = new DefaultDockerImageMigrationHandler(url);

        assertThatExceptionOfType(IllegalArgumentException.class).
                isThrownBy(() -> migrationHandler.performMigration(TEST_NODE));
    }

    private String noopScript() {
        return script("");
    }

    private String script(String content) {
        return "module.exports = function(input) { " + content + "};";
    }
}