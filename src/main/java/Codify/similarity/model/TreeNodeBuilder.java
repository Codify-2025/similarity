package Codify.similarity.model;

import com.fasterxml.jackson.databind.JsonNode;

public class TreeNodeBuilder {
    public static TreeNode fromJson(JsonNode json) {
        String label = json.has("type") ? json.get("type").asText() : "Unknown";
        TreeNode node = new TreeNode(label);

        if (json.has("children")) {
            for (JsonNode child : json.get("children")) {
                node.addChild(fromJson(child));
            }
        }

        return node;
    }
}