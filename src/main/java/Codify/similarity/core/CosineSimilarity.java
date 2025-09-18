package Codify.similarity.core;

import java.util.Map;
import java.util.Set;

public class CosineSimilarity {

    public static double calculate(Map<String, Integer> vec1, Map<String, Integer> vec2) {
        Set<String> allKeys = new java.util.HashSet<>(vec1.keySet());
        allKeys.addAll(vec2.keySet());

        double dot = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (String key : allKeys) {
            int v1 = vec1.getOrDefault(key, 0);
            int v2 = vec2.getOrDefault(key, 0);
            dot += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }

        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}