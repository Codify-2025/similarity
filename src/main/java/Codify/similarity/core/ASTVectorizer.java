package Codify.similarity.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;

public class ASTVectorizer {
    public static Map<String, Integer> buildTypeVector(JsonNode root) {
        Map<String, Integer> vector = new HashMap<>();
        traverse(root, vector);
        return vector;
    }

    private static void traverse(JsonNode node, Map<String, Integer> vector) {
        if (node.has("type")) {
            String type = node.get("type").asText();
            vector.put(type, vector.getOrDefault(type, 0) + 1);
        }

        if (node.has("children")) {
            for (JsonNode child : node.get("children")) {
                traverse(child, vector);
            }
        }
    }
}