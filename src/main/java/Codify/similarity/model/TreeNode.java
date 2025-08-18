package Codify.similarity.model;

import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    public String label; // 노드 유형
    public List<TreeNode> children = new ArrayList<>();

    public TreeNode(String label) { // 라벨 값을 지정하여 노드 생성
        this.label = label;
    }

    public void addChild(TreeNode child) { // 자식 노드를 리스트에 추가
        children.add(child);
    }
}