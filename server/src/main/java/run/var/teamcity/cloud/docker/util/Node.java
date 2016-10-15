package run.var.teamcity.cloud.docker.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Immutable JSON node. Nodes instanced from this class are safe to use from multiple threads.
 */
public class Node extends AbstractNode<Node> {

    /**
     * Constant node for an empty array.
     */
    public static Node EMPTY_ARRAY = new Node(OBJECT_MAPPER.createArrayNode());

    /**
     * Constant node for an empty object.
     */
    public static Node EMPTY_OBJECT = new Node(OBJECT_MAPPER.createObjectNode());

    Node(JsonNode node) {
        super(node);
    }

    @Override
    Node newNode(JsonNode node) {
        return new Node(node);
    }

    public static Node parse(InputStream jsonStream) throws IOException {
        return new Node(OBJECT_MAPPER.readTree(jsonStream));
    }

    public static Node parse(String json) throws IOException {
        return new Node(OBJECT_MAPPER.readTree(json));
    }

    /**
     * Creates an editable node using this location as root. Changes made on the returned instance will not affect this
     * node. The returned instance can be made immutable again by calling {@link EditableNode#saveNode()}.
     *
     * @return an editable using this node location as content root
     */
    public EditableNode editNode() {
        return new EditableNode(node.deepCopy());
    }
}
