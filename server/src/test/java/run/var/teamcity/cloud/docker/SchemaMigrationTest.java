package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

public class SchemaMigrationTest {

    @Test
    public void v1Migration() {
        EditableNode root = Node.EMPTY_OBJECT.editNode();
        root.getOrCreateObject("Administration").put("Version", 1);
        EditableNode hostConfig = root.getOrCreateObject("Container").getOrCreateObject("HostConfig");

        hostConfig.getOrCreateArray("Binds").
                add("/tmp/host_path:/tmp/container_path:rw").
                add("/tmp/host_path2:volume:ro");

        Node result = migrate(root.saveNode());

        Node binds = result.getObject("Editor").getArray("Binds");
        EditableNode expected = Node.EMPTY_ARRAY.editNode();

        expected.addObject().
                put("PathOnHost", "/tmp/host_path").
                put("PathInContainer", "/tmp/container_path").
                put("ReadOnly", "rw");

        expected.addObject().
                put("PathOnHost", "/tmp/host_path2").
                put("PathInContainer", "volume").
                put("ReadOnly", "ro");

        assertThat(binds).isEqualTo(expected);

        root = Node.EMPTY_OBJECT.editNode();
        root.getOrCreateObject("Administration").put("Version", 1);

        hostConfig = root.getOrCreateObject("Container").getOrCreateObject("HostConfig");

        hostConfig.getOrCreateArray("Binds").
                add("C:\\host_path:C:\\container_path:rw").
                add("C:\\host_path:volume:ro");

        result = migrate(root.saveNode());

        binds = result.getObject("Editor").getArray("Binds");
        expected = Node.EMPTY_ARRAY.editNode();

        expected.addObject().
                put("PathOnHost", "C:\\host_path").
                put("PathInContainer", "C:\\container_path").
                put("ReadOnly", "rw");

        expected.addObject().
                put("PathOnHost", "C:\\host_path").
                put("PathInContainer", "volume").
                put("ReadOnly", "ro");

        assertThat(binds).isEqualTo(expected);
    }

    @Test
    public void v2Migration() {
        EditableNode root = Node.EMPTY_OBJECT.editNode();
        root.getOrCreateObject("Administration").put("Version", 2);

        Node result = migrate(root.saveNode());

        assertThat(result.getObject("Administration").getAsBoolean("PullOnCreate")).isTrue();

        root = Node.EMPTY_OBJECT.editNode();
        root.getOrCreateObject("Administration").put("Version", 3).put("PullOnCreate", false);

        result = migrate(root.saveNode());

        assertThat(result.getObject("Administration").getAsBoolean("PullOnCreate")).isFalse();
    }

    @Test
    public void v3Migration() {

        EditableNode root = Node.EMPTY_OBJECT.editNode();

        root.getOrCreateObject("Administration").put("Version", 3);

        v3MigrationTestUnitConversion("MemorySwap", "MemorySwapUnit");
        v3MigrationTestUnitConversion("Memory", "MemoryUnit");
    }

    @Test
    public void v4Migration() {

        EditableNode root = Node.EMPTY_OBJECT.editNode();

        root.getOrCreateObject("Administration").put("Version", 4);

        Node result = migrate(root.saveNode());

        assertThat(result).isEqualTo(root);

        root = Node.EMPTY_OBJECT.editNode();

        root.getOrCreateObject("Administration").put("Version", 4);
        root.getOrCreateObject("Container").put("foo", "bar");

        result = migrate(root.saveNode());

        EditableNode expected = Node.EMPTY_OBJECT.editNode();

        expected.getOrCreateObject("Administration").put("Version", 4);
        expected.getOrCreateObject("AgentHolderSpec").put("foo", "bar");

        assertThat(result).isEqualTo(expected);
    }

    private void v3MigrationTestUnitConversion(String valueKey, String unitKey) {
        final Map<String, Integer> legacyUnitMultipliers = mapOf(pair("GiB", 134217728), pair("MiB", 131072), pair("bytes",
                1));
        final Map<String, Integer> unitMultipliers = mapOf(pair("GiB", 1073741824), pair("MiB", 1048576), pair
                        ("KiB", 1024), pair("bytes", 1));

        legacyUnitMultipliers.forEach((unit, multiplier) -> {
            IntStream.rangeClosed(1,3).forEach( value -> {
                EditableNode root = Node.EMPTY_OBJECT.editNode();
                root.getOrCreateObject("Administration").put("Version", 3);
                root.getOrCreateObject("Editor").put(unitKey, unit);
                root.getOrCreateObject("Container").getOrCreateObject("HostConfig").put(valueKey, value * multiplier);
                Node result = migrate(root.saveNode());

                assertThat(result.getObject("AgentHolderSpec").getObject("HostConfig").getAsLong(valueKey)).
                        isEqualTo(((long) value) * unitMultipliers.get(unit));
                    }
            );

            EditableNode root = Node.EMPTY_OBJECT.editNode();
            root.getOrCreateObject("Administration").put("Version", 3);
            root.getOrCreateObject("Container").getOrCreateObject("HostConfig").put(valueKey, -1);
            root.getOrCreateObject("Editor").put(unitKey, unit);

            Node result = migrate(root.saveNode());

            assertThat(result.getObject("AgentHolderSpec").getObject("HostConfig").getAsLong(valueKey)).isEqualTo(-1L);

            root = Node.EMPTY_OBJECT.editNode();
            root.getOrCreateObject("Administration").put("Version", 3);
            root.getOrCreateObject("Container").getOrCreateObject("HostConfig").put(valueKey, -1);

            result = migrate(root.saveNode());

            assertThat(result.getObject("AgentHolderSpec").getObject("HostConfig").getAsLong(valueKey)).isEqualTo(-1L);
        });
    }


    private Node migrate(Node input) {
        return new DefaultDockerImageMigrationHandler(DefaultDockerCloudSupport.VANILLA_MIGRATION_SCRIPT)
                .performMigration(input);
    }
}
