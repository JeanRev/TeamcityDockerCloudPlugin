package run.var.teamcity.cloud.docker.util;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON node abstraction. This is just an overlay to a JSON backend. It provides a relatively strict validation, and
 * does not assume mutability of the nodes.
 *
 * <p>The current JSON backend is Jackson.</p>
 *
 * @param <N> <em>this</em> node type
 */
abstract class AbstractNode<N extends AbstractNode> {

    final static JsonFactory JSON_FACTORY = new JsonFactory();
    final static ObjectMapper OBJECT_MAPPER = new ObjectMapper(JSON_FACTORY);

    final JsonNode node;

    AbstractNode(JsonNode node) {
        this.node = node;
    }

    /**
     * Returns all children nodes of this array.
     *
     * @return the list of children nodes
     *
     * @throws UnsupportedOperationException if this node is not an array
     */
    @NotNull
    public List<N> getArrayValues() {
        checkArray();
        List<N> arrayValues = new ArrayList<>(node.size());
        for (JsonNode child : node) {
            arrayValues.add(newNode(child));
        }

        return arrayValues;
    }

    /**
     * Returns all key/node mappings of this object.
     *
     * @return the key/node mappings
     *
     * @throws UnsupportedOperationException if this node is not an object
     */
    @NotNull
    public Map<String, N> getObjectValues() {
        checkObject();
        Map<String, N> objectValues = new LinkedHashMap<>(node.size());
        Iterator<String> fieldNames = node.fieldNames();
        while(fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            objectValues.put(fieldName, newNode(node.get(fieldName)));
        }

        return objectValues;
    }


    /**
     * Helper method to cast the backend node as an object node.
     *
     * @return the backend node as an object node
     *
     * @throws UnsupportedOperationException if this node is not an object
     */
    @NotNull
    final ObjectNode getObjectNode() {
        checkObject();
        return (ObjectNode) node;
    }

    /**
     * Helper method to cast the backend node as an array node
     *
     * @return the backend node as an array node
     *
     * @throws UnsupportedOperationException if this node is not an array
     */
    final ArrayNode getArrayNode() {
        checkArray();
        return (ArrayNode) node;
    }

    /**
     * Gets the child object node with the specified name.
     *
     * @param fieldName the child field name
     *
     * @return the child node
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node does not exists or is
     * not an object
     */
    @NotNull
    public N getObject(@NotNull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode object = node.get(fieldName);
        if (!(object instanceof ObjectNode)) {
            throw new UnsupportedOperationException("Child field not found or not an object: " + node + " / " +
                    fieldName);
        }
        return newNode(object);
    }

    /**
     * Gets the child object node with the specified name.
     *
     * @param fieldName the child field name
     * @param def the default value to be used if the child node does not exists
     *
     * @return the child node or the provided default value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node or the child node is not an object
     */
    public N getObject(@NotNull String fieldName, @Nullable N def) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        if (def != null && ! ((AbstractNode) def).isObject()) {
            throw new IllegalArgumentException("Default node value is not an object.");
        }
        JsonNode object = node.get(fieldName);
        if (object == null) {
            return def;
        }
        return getObject(fieldName);
    }

    /**
     * Gets the child array node with the specified name.
     *
     * @param fieldName the child field name
     *
     * @return the child node
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node does not exists or is
     * not an array
     */
    public N getArray(@NotNull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode object = node.get(fieldName);
        if (!(object instanceof ArrayNode)) {
            throw new UnsupportedOperationException("Child field not found or not an array: " + node + " / " +
                    fieldName);
        }
        return newNode(object);
    }

    /**
     * Gets the child array node with the specified name.
     *
     * @param fieldName the child field name
     * @param def the default value to be used if the child node does not exists
     *
     * @return the child node or the provided default value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node is not an array
     */
    public N getArray(@NotNull String fieldName, @Nullable N def) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        if (def != null && !((AbstractNode) def).isArray()) {
            throw new IllegalArgumentException("Default node value is not an array.");
        }
        JsonNode object = node.get(fieldName);
        if (object == null) {
            return def;
        }

        return getArray(fieldName);
    }

    /**
     * Gets the child integer value with the specified name.
     *
     * @param fieldName the child field name
     *
     * @return the integer value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node does not exists
     * or is not an integer value node
     */
    public int getAsInt(@NotNull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode value = node.get(fieldName);
        if (value == null || !value.canConvertToInt()) {
            throw new UnsupportedOperationException("Child field not found or is not an integer value node: " +
                    node + " / " +  fieldName);
        }
        return value.asInt();
    }

    /**
     * Gets the child integer node with the specified name.
     *
     * @param fieldName the child field name
     * @param def the default value to be used if the child value node does not exists
     *
     * @return the child node or the provided default value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node is not an integer
     * value node
     */
    public int getAsInt(@NotNull String fieldName, int def) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode value = node.get(fieldName);
        if (value == null) {
            return def;
        }

        return getAsInt(fieldName);
    }

    /**
     * Gets the child integer value with the specified name.
     *
     * @param fieldName the child field name
     *
     * @return the integer value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node does not exists
     * or is not an integer value node
     */
    public BigInteger getAsBigInt(@NotNull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();

        BigInteger bigInt = null;
        JsonNode value = node.get(fieldName);
        if (value != null && value.isIntegralNumber()) {
            String number = value.asText();
            bigInt = new BigInteger(number);
        }
        if (bigInt == null) {
            throw new UnsupportedOperationException("Child field not found or is not an integer value node: " +
                    node + " / " +  fieldName);
        }
        return bigInt;
    }

    /**
     * Gets the child integer node with the specified name.
     *
     * @param fieldName the child field name
     * @param def the default value to be used if the child value node does not exists
     *
     * @return the child node or the provided default value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node is not an integer
     * value node
     */
    public BigInteger getAsBigInt(@NotNull String fieldName, BigInteger def) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode value = node.get(fieldName);
        if (value == null) {
            return def;
        }

        return getAsBigInt(fieldName);
    }

    /**
     * Gets the child integer value with the specified name.
     *
     * @param fieldName the child field name
     *
     * @return the integer value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node does not exists
     * or is not an integer value node
     */
    public long getAsLong(@NotNull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode value = node.get(fieldName);
        if (value == null || !value.canConvertToLong()) {
            throw new UnsupportedOperationException("Child field not found or is not an long value node: " +
                    node + " / " +  fieldName);
        }
        return value.asLong();
    }

    /**
     * Gets the child boolean value with the specified name.
     *
     * @param fieldName the child field name
     *
     * @return the boolean value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node does not exists
     * or is not an boolean value node
     */
    public boolean getAsBoolean(@NotNull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isBoolean()) {
            throw new UnsupportedOperationException("Child field not found or is not an boolean value node: " +
                    node + " / " +  fieldName);
        }

        return value.asBoolean();
    }


    /**
     * Gets the child boolean node with the specified name.
     *
     * @param fieldName the child field name
     * @param def the default value to be used if the child value node does not exists
     *
     * @return the child node or the provided default value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node is not a boolean
     * value node
     */
    @Nullable
    public Boolean getAsBoolean(@NotNull String fieldName, @Nullable Boolean def) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode value = node.get(fieldName);
        if (value == null) {
            return def;
        }

        return getAsBoolean(fieldName);
    }

    /**
     * Gets the child string value with the specified name.
     *
     * @param fieldName the child field name
     *
     * @return the boolean value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node does not exists
     * or is not a string value node
     */
    @NotNull
    public String getAsString(@NotNull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual()) {
            throw new UnsupportedOperationException("Child field not found or is not a string value node: " +
                    node + " / " +  fieldName);
        }
        return value.asText();
    }

    /**
     * Gets the child text node with the specified name.
     *
     * @param fieldName the child field name
     * @param def the default value to be used if the child value node does not exists
     *
     * @return the child node or the provided default value
     *
     * @throws NullPointerException if {@code fieldName} is {@code null}
     * @throws UnsupportedOperationException if this node is not an object, or if the child node is not a text
     * value node
     */
    @Nullable
    public String getAsString(@NotNull String fieldName, @Nullable String def) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        checkObject();
        JsonNode value = node.get(fieldName);
        if (value == null) {
            return def;
        }

        return getAsString(fieldName);
    }

    /**
     * Gets the string value of this node.
     *
     * @return the string value
     */
    @NotNull
    public String getAsString() {
        if (!node.isTextual()) {
            throw new UnsupportedOperationException("Not a text node: " + node);
        }
        return node.asText();
    }

    private void checkArray() {
        if (!isArray()) {
            throw new UnsupportedOperationException("Not an array: " + node);
        }
    }

    private void checkObject() {
        if (!isObject()) {
            throw new UnsupportedOperationException("Not an object: " + node);
        }
    }

    private boolean isObject() {
        return node instanceof ObjectNode;
    }

    private boolean isArray() {
        return node instanceof ArrayNode;
    }

    @Override
    public String toString() {
        return node.toString();
    }

    abstract N newNode(JsonNode node);
}
