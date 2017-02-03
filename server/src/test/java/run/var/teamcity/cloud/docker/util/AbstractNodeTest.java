package run.var.teamcity.cloud.docker.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("unchecked")
public abstract class AbstractNodeTest<N extends AbstractNode> {

    @Test
    public void getArrayValues() {
        AbstractNode<N> node = stringArrayNode("A", "B", "C");
        List<String> values = node.getArrayValues().stream().
                map(AbstractNode::getAsString).
                collect(Collectors.toList());
        assertThat(values).containsExactly("A", "B", "C");
    }

    @Test
    public void getArrayValuesNotAnArray() {
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> emptyNode().getArrayValues());
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> objectNode(pair("A", "1"), pair("B", "2")).getArrayValues());
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> stringNode("A").getArrayValues());
    }

    @Test
    public void getObjectValues() {
        AbstractNode<N> node = objectNode(pair("A", "1"), pair("B", "2"), pair("C", "3"));
        Map<String, String> values = node.getObjectValues().entrySet().stream().
                collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAsString()));
        assertThat(values).containsExactly(entry("A", "1"), entry("B", "2"), entry("C", "3"));

        assertThat(emptyNode().getObjectValues()).isEmpty();
    }

    @Test
    public void getObjectValuesNotAnObject() {
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> stringArrayNode("A", "B", "C").getObjectValues());
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> stringNode("A").getObjectValues());
    }

    @Test
    public void getObject() {
        AbstractNode<N> node = parentWithChildObject(pair("A", "1"));

        assertThat(node.getObject("child").getAsString("A")).isEqualTo("1");
        assertThat(node.getObject("unknownChild", null)).isNull();
        @SuppressWarnings("unchecked") N def = (N) emptyNode();
        assertThat(node.getObject("unknownChild", def)).isSameAs(def);
    }

    @Test
    public void getObjectNotAnObject() {
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> parentWithChildArray("A", "B", "C").getObject("child"));
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> stringNode("A").getObject("child"));

    }

    @Test
    public void getArray() {

        AbstractNode<N> node = parentWithChildArray("A", "B", "C");

        List<String> values = ((List<AbstractNode<N>>) node.getArray("child").getArrayValues()).stream().map
                (AbstractNode::getAsString).collect(Collectors.toList());
        assertThat(values).containsExactly("A", "B", "C");
        assertThat(node.getArray("unknownChild", null)).isNull();

        @SuppressWarnings("unchecked") N def = (N) stringArrayNode();
        assertThat(node.getArray("unknownChild", def)).isSameAs(def);
    }

    @Test
    public void getArrayNotAnArray() {
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> parentWithChildObject(pair("A", "1")).getArray("child"));
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> stringNode("A").getArray("child"));
    }

    @Test
    public void getAsInt() {
        AbstractNode<N> node = parentWithChildInt(42);
        assertThat(node.getAsInt("child")).isEqualTo(42);
        assertThat(node.getAsInt("unknownChild", 0)).isEqualTo(0);
    }

    @Test
    public void getAsIntNotAnInt() {
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> parentWithChildString("A").getAsInt("child"));
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> stringNode("A").getAsInt("child"));
    }

    @Test
    public void getAsLong() {
        AbstractNode<N> node = parentWithChildInt(42);
        assertThat(node.getAsLong("child")).isEqualTo(42);
    }

    @Test
    public void getAsBoolean() {
        AbstractNode<N> node = parentWithChildBoolean(Boolean.TRUE);
        assertThat(node.getAsBoolean("child")).isTrue();
        assertThat(node.getAsBoolean("unknownChild", Boolean.FALSE)).isFalse();
    }

    @Test
    public void getAsBooleanNotABoolean() {
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> parentWithChildString("A").getAsBoolean("child"));
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> stringNode("A").getAsBoolean("child"));
    }

    @Test
    public void getAsString() {
        AbstractNode<N> node = parentWithChildString("A");
        assertThat(node.getAsString("child")).isEqualTo("A");
        assertThat(node.getAsString("unknownChild", "B")).isEqualTo("B");
    }

    @Test
    public void getAsStringNotAString() {
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> parentWithChildInt(42).getAsString("child"));
        assertThatExceptionOfType(UnsupportedOperationException.class).
                isThrownBy(() -> intNode(42).getAsInt("child"));
    }

    @Test
    public void getAsBigInt() {
        AbstractNode<N> node = parentWithChildInt(42);
        assertThat(node.getAsBigInt("child")).isEqualTo(BigInteger.valueOf(42));
        assertThat(node.getAsBigInt("unknownChild", BigInteger.ZERO)).isEqualTo(BigInteger.ZERO);
    }

    protected AbstractNode<N> parentWithChildString(String value) {
        ObjectNode parent = AbstractNode.OBJECT_MAPPER.createObjectNode();
        parent.put("child", value);
        return createNode(parent);
    }

    protected AbstractNode<N> parentWithChildInt(int value) {
        ObjectNode parent = AbstractNode.OBJECT_MAPPER.createObjectNode();
        parent.put("child", value);
        return createNode(parent);
    }

    protected AbstractNode<N> parentWithChildBoolean(Boolean value) {
        ObjectNode parent = AbstractNode.OBJECT_MAPPER.createObjectNode();
        parent.put("child", value);
        return createNode(parent);
    }

    protected AbstractNode<N> parentWithChildArray(String... values) {
        ObjectNode parent = AbstractNode.OBJECT_MAPPER.createObjectNode();
        ArrayNode array = AbstractNode.OBJECT_MAPPER.createArrayNode();
        Arrays.stream(values).forEach(array::add);
        parent.set("child", array);
        return createNode(parent);
    }

    protected AbstractNode<N> parentWithChildObject(Pair... pairs) {
        ObjectNode parent = AbstractNode.OBJECT_MAPPER.createObjectNode();
        ObjectNode child = AbstractNode.OBJECT_MAPPER.createObjectNode();
        Arrays.stream(pairs).forEach(p -> child.put(p.key, p.value));
        parent.set("child", child);
        return createNode(parent);
    }

    protected AbstractNode<N> intNode(int value) {
        return createNode(IntNode.valueOf(value));
    }

    protected AbstractNode<N> stringNode(String value) {
        return createNode(TextNode.valueOf(value));
    }

    protected AbstractNode<N> stringArrayNode(String... values) {
        ArrayNode array = AbstractNode.OBJECT_MAPPER.createArrayNode();
        Arrays.stream(values).forEach(array::add);
        return createNode(array);
    }

    protected AbstractNode<N> objectNode(Pair... pairs) {
        ObjectNode node = AbstractNode.OBJECT_MAPPER.createObjectNode();
        Arrays.stream(pairs).forEach(p -> node.put(p.key, p.value));
        return createNode(node);
    }

    protected AbstractNode<N> emptyNode() {
        return createNode(AbstractNode.OBJECT_MAPPER.createObjectNode());
    }

    protected abstract AbstractNode<N> createNode(JsonNode node);

    private Pair pair(String key, String value) {
        return new Pair(key, value);
    }

    private static class Pair {
        final String key;
        final String value;

        Pair(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}