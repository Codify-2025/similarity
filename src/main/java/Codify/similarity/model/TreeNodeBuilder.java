package Codify.similarity.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TreeNodeBuilder {
    public static TreeNode fromJson(JsonNode json) {
        // JSON에서 line/value도 읽도록 수정
        String type = json.has("type") ? json.get("type").asText() : "Unknown";
        TreeNode node = new TreeNode(type);

        if (json.has("value")) node.value = json.get("value").asText(null);
        
        // MongoDB의 NumberInt 개선
        if (json.has("line")) {
            JsonNode lineNode = json.get("line");
            if (lineNode.isNumber()) {
                node.line = lineNode.asInt(-1);
            } else if (lineNode.isObject() && lineNode.has("$numberInt")) {
                // MongoDB NumberInt 형식 처리
                node.line = lineNode.get("$numberInt").asInt(-1);
            } else if (lineNode.isTextual()) {
                // 문자열로 된 숫자 처리
                try {
                    node.line = Integer.parseInt(lineNode.asText());
                } catch (NumberFormatException e) {
                    node.line = -1;
                }
            } else {
                node.line = -1;
            }
            
            // 디버깅 로그
            if (node.line > 0 && log.isDebugEnabled()) {
                log.debug("Node {} at line {}", type, node.line);
            }
        }
        
        if (json.has("children")) {
            for (JsonNode ch : json.get("children")) {
                node.addChild(fromJson(ch));
            }
        }
        return node;
    }
}