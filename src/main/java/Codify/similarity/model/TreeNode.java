package Codify.similarity.model;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TreeNode {
    public String label; // 노드 유형
    public List<TreeNode> children = new ArrayList<>();
    // 유사도 코드 비교
    public String value;
    public int line = -1;

    // span
    public int minLine = -1, maxLine = -1;

    public TreeNode(String label) { // 라벨 값을 지정하여 노드 생성
        this.label = label;
    }

    public void addChild(TreeNode child) { // 자식 노드를 리스트에 추가
        children.add(child);
    }

    // 유사도 코드 비교
    public void computeSpan() {
        int min = (line >= 1) ? line : Integer.MAX_VALUE;
        int max = (line >= 1) ? line : Integer.MIN_VALUE;
        
        for (TreeNode ch : children) {
            ch.computeSpan();
            if (ch.minLine >= 1) min = Math.min(min, ch.minLine);
            if (ch.maxLine >= 1) max = Math.max(max, ch.maxLine);
        }
        
        if (min == Integer.MAX_VALUE) { 
            minLine = -1; 
            maxLine = -1; 
        } else { 
            minLine = min; 
            maxLine = max; 
        }
        
        // 디버깅 로그
        if (log.isDebugEnabled() && minLine >= 1) {
            log.debug("Node {} span: [{}-{}] (self line: {})", 
                label, minLine, maxLine, line);
        }
    }
}