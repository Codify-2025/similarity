package Codify.similarity.core;

import Codify.similarity.model.TreeNode;

public class EditCost {
    public static int insertCost(TreeNode node) {
        return 1;
    }

    public static int deleteCost(TreeNode node) {
        return 1;
    }

    public static int renameCost(TreeNode a, TreeNode b) {
        return a.label.equals(b.label) ? 0 : 1;
    }
}
