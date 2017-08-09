package run.var.teamcity.cloud.docker.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

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
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> emptyNode().getArrayValues());
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> objectNode(pair("A", "1"), pair("B", "2")).getArrayValues());
        assertThatExceptionOfType(NodeProcessingException.class).
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
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> stringArrayNode("A", "B", "C").getObjectValues());
        assertThatExceptionOfType(NodeProcessingException.class).
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
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> parentWithChildArray("A", "B", "C").getObject("child"));
        assertThatExceptionOfType(NodeProcessingException.class).
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
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> parentWithChildObject(pair("A", "1")).getArray("child"));
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> stringNode("A").getArray("child"));
    }

    @Test
    public void getAsInt() {
        AbstractNode<N> node = parentWithChildInt(42);
        assertThat(node.getAsInt("child")).isEqualTo(42);
        node = parentWithChildInt(Integer.MAX_VALUE);
        assertThat(node.getAsInt("child")).isEqualTo(Integer.MAX_VALUE);
        node = parentWithChildInt(Integer.MIN_VALUE);
        assertThat(node.getAsInt("child")).isEqualTo(Integer.MIN_VALUE);
        assertThat(node.getAsInt("unknownChild", 0)).isEqualTo(0);
    }

    @Test
    public void getAsIntOutOfBound() {
        AbstractNode<N> nodeMax = parentWithChildLong(Integer.MAX_VALUE + 1L);
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> nodeMax.getAsInt("child"));
        AbstractNode<N> nodeMin = parentWithChildLong(Integer.MIN_VALUE - 1L);
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> nodeMin.getAsInt("child"));
    }

    @Test
    public void getAsIntFromDecimal() {
        AbstractNode<N> node = parentWithChildDouble(1.0);
        assertThat(node.getAsInt("child")).isEqualTo(1);

        node = parentWithChildBigDecimal(new BigDecimal("1E+1"));
        assertThat(node.getAsInt("child")).isEqualTo(10);

        AbstractNode<N> nodeWithFraction = parentWithChildBigDecimal(BigDecimal.valueOf(1.1));
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> nodeWithFraction.getAsInt("child"));

    }

    @Test
    public void getAsIntNotAnInt() {
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> parentWithChildString("A").getAsInt("child"));
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> stringNode("A").getAsInt("child"));
    }

    @Test
    public void getAsLong() {
        AbstractNode<N> node = parentWithChildInt(42);
        assertThat(node.getAsLong("child")).isEqualTo(42);
        node = parentWithChildLong(Long.MAX_VALUE);
        assertThat(node.getAsLong("child")).isEqualTo(Long.MAX_VALUE);
        node = parentWithChildLong(Long.MIN_VALUE);
        assertThat(node.getAsLong("child")).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    public void getAsLongOutOfBound() {
        AbstractNode<N> nodeMax = parentWithChildBigInteger(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> nodeMax.getAsLong("child"));
        AbstractNode<N> nodeMin = parentWithChildBigInteger(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE));
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> nodeMin.getAsLong("child"));
    }

    @Test
    public void getAsLongFromDecimal() {
        AbstractNode<N> node = parentWithChildDouble(1.0);
        assertThat(node.getAsLong("child")).isEqualTo(1L);

        node = parentWithChildBigDecimal(new BigDecimal("1E+1"));
        assertThat(node.getAsLong("child")).isEqualTo(10L);

        AbstractNode<N> nodeWithFraction = parentWithChildBigDecimal(BigDecimal.valueOf(1.1));
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> nodeWithFraction.getAsLong("child"));
    }

    @Test
    public void getAsBoolean() {
        AbstractNode<N> node = parentWithChildBoolean(Boolean.TRUE);
        assertThat(node.getAsBoolean("child")).isTrue();
        assertThat(node.getAsBoolean("unknownChild", Boolean.FALSE)).isFalse();
    }

    @Test
    public void getAsBooleanNotABoolean() {
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> parentWithChildString("A").getAsBoolean("child"));
        assertThatExceptionOfType(NodeProcessingException.class).
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
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> parentWithChildInt(42).getAsString("child"));
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> intNode(42).getAsInt("child"));
    }

    @Test
    public void getAsBigInt() {
        AbstractNode<N> node = parentWithChildInt(42);
        assertThat(node.getAsBigInt("child")).isEqualTo(BigInteger.valueOf(42));
        assertThat(node.getAsBigInt("unknownChild", BigInteger.ZERO)).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void getAsBigIntFromDecimal() {
        AbstractNode<N> node = parentWithChildDouble(1.0);
        assertThat(node.getAsBigInt("child")).isEqualTo(BigInteger.ONE);

        node = parentWithChildDouble(1.0);
        assertThat(node.getAsBigInt("child")).isEqualTo(BigInteger.ONE);

        node = parentWithChildBigDecimal(new BigDecimal("1E+1"));
        assertThat(node.getAsBigInt("child")).isEqualTo(BigInteger.TEN);

        AbstractNode<N> nodeWithFraction = parentWithChildBigDecimal(BigDecimal.valueOf(1.1));
        assertThatExceptionOfType(NodeProcessingException.class).
                isThrownBy(() -> nodeWithFraction.getAsBigInt("child"));
    }

    @Test
    public void isNull() {
        AbstractNode<N> node = parentWithChildString("A");
        assertThat(node.getObjectValues().get("child").isNull()).isFalse();
        node = parentWithChildNull();
        assertThat(node.getObjectValues().get("child").isNull()).isTrue();
    }

    protected AbstractNode<N> parentWithChildNull() {
        ObjectNode parent = AbstractNode.OBJECT_MAPPER.createObjectNode();
        parent.putNull("child");
        return createNode(parent);
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

    protected AbstractNode<N> parentWithChildLong(long value) {
        ObjectNode parent = AbstractNode.OBJECT_MAPPER.createObjectNode();
        parent.put("child", value);
        return createNode(parent);
    }

    protected AbstractNode<N> parentWithChildDouble(double value) {
        ObjectNode parent = AbstractNode.OBJECT_MAPPER.createObjectNode();
        parent.put("child", value);
        return createNode(parent);
    }

    protected AbstractNode<N> parentWithChildBigInteger(BigInteger value) {
        return parentWithChildBigDecimal(new BigDecimal(value));
    }

    protected AbstractNode<N> parentWithChildBigDecimal(BigDecimal value) {
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