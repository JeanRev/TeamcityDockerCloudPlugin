package run.var.teamcity.cloud.docker.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;

/**
 * An editable JSON node. Each editable node has a reference to the root of the edition tree, which is the place where
 * the edition started.
 *
 * <p>Instances of this class are not thread safe.</p>
 */
public class EditableNode extends AbstractNode<EditableNode> {

    EditableNode(JsonNode node) {
        super(node);
    }

    @Nonnull
    public EditableNode add(@Nullable String value) {
        getArrayNode().add(value);
        return this;
    }

    @Nonnull
    public EditableNode addObject() {
        return newNode(getArrayNode().addObject());
    }

    @Nonnull
    public EditableNode put(@Nonnull String key, Boolean value) {
        DockerCloudUtils.requireNonNull(key, "Key cannot be null.");
        getObjectNode().put(key, value);
        return this;
    }

    @Nonnull
    public EditableNode put(@Nonnull String key, Integer value) {
        DockerCloudUtils.requireNonNull(key, "Key cannot be null.");
        getObjectNode().put(key, value);
        return this;
    }

    @Nonnull
    public EditableNode put(@Nonnull String key, BigDecimal value) {
        DockerCloudUtils.requireNonNull(key, "Key cannot be null.");
        getObjectNode().put(key, value);
        return this;
    }

    @Nonnull
    public EditableNode put(@Nonnull String key, Long value) {
        DockerCloudUtils.requireNonNull(key, "Key cannot be null.");
        getObjectNode().put(key, value);
        return this;
    }

    @Nonnull
    public EditableNode put(@Nonnull String key, @Nonnull Node node) {
        DockerCloudUtils.requireNonNull(key, "Key cannot be null.");
        getObjectNode().set(key, node.node.deepCopy());
        return this;
    }

    @Nonnull
    public EditableNode put(@Nonnull String key, @Nullable Object value) {
        DockerCloudUtils.requireNonNull(key, "Key cannot be null.");
        getObjectNode().put(key, value != null ? value.toString() : null);
        return this;
    }

    @Nonnull
    public EditableNode getOrCreateObject(@Nonnull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        return newNode(getObjectNode().with(fieldName));
    }

    @Nonnull
    public EditableNode getOrCreateArray(@Nonnull String fieldName) {
        DockerCloudUtils.requireNonNull(fieldName, "Field name cannot be null.");
        ObjectNode objectNode = getObjectNode();
        JsonNode childNode = objectNode.get(fieldName);
        if (childNode != null && !(childNode instanceof ArrayNode)) {
            throw new NodeProcessingException(
                    "Child node exists but is not an array: " + node + " / " + fieldName);
        }
        return newNode(node.withArray(fieldName));
    }

    /**
     * Creates an immutable JSON tree starting at the edition root. The edition root is located where the edition was
     * started, <strong>it is not necessarily the current node location</strong>.
     *
     * @return the immutable node at the root of the edited tree
     *
     * @see Node#editNode()
     */
    public Node saveNode() {
        return new Node(node.deepCopy());
    }

    @Override
    EditableNode newNode(JsonNode node) {
        return new EditableNode(node);
    }

    public static EditableNode newEditableNode() {
        return new EditableNode(OBJECT_MAPPER.createObjectNode());
    }
}
