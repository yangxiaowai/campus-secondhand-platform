package com.campus.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TF-IDF 向量与余弦相似度（语义检索基础）
 */
public final class TfIdfVectorUtil {

    private TfIdfVectorUtil() {
    }

    public static Map<String, Double> buildQueryVector(List<String> terms, Map<String, Integer> docFreq, int totalDocs) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : terms) {
            if (t == null || t.isEmpty()) {
                continue;
            }
            tf.merge(t, 1, Integer::sum);
        }
        return buildTfIdf(tf, docFreq, totalDocs);
    }

    public static Map<String, Double> buildTfIdf(Map<String, Integer> termFreq,
                                               Map<String, Integer> docFreq,
                                               int totalDocs) {
        Map<String, Double> vec = new HashMap<>();
        if (termFreq == null || termFreq.isEmpty() || totalDocs <= 0) {
            return vec;
        }
        int maxTf = termFreq.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        for (Map.Entry<String, Integer> e : termFreq.entrySet()) {
            String term = e.getKey();
            int tf = e.getValue() == null ? 0 : e.getValue();
            if (tf <= 0) {
                continue;
            }
            int df = docFreq != null && docFreq.containsKey(term) ? docFreq.get(term) : 1;
            double idf = Math.log((totalDocs + 1.0) / (df + 1.0)) + 1.0;
            double normalizedTf = 0.5 + 0.5 * tf / maxTf;
            vec.put(term, normalizedTf * idf);
        }
        return vec;
    }

    public static double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            double va = e.getValue() == null ? 0.0 : e.getValue();
            normA += va * va;
            Double vb = b.get(e.getKey());
            if (vb != null) {
                dot += va * vb;
            }
        }
        for (double vb : b.values()) {
            normB += vb * vb;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static double keywordScore(int matchedTerms, int totalQueryTerms, int fieldHits) {
        if (totalQueryTerms <= 0) {
            return 0.0;
        }
        double coverage = matchedTerms * 1.0 / totalQueryTerms;
        double density = Math.min(1.0, fieldHits * 0.15);
        return coverage * 0.75 + density * 0.25;
    }
}
