package com.campus.search.embedding;

import java.util.Map;

/**
 * search:vec:{productId} 存储结构（v2 支持真实 Embedding + 兼容 v1 TF-IDF）
 */
public class ProductVectorDocument {

    public static final int VERSION_DENSE = 2;
    public static final int VERSION_TFIDF_ONLY = 1;

    private int version;
    private String model;
    private int dims;
    /** 稀疏 TF-IDF（降级 / 混合特征） */
    private Map<String, Double> tfidf;
    /** 稠密 Embedding 向量 */
    private float[] embedding;

    public ProductVectorDocument() {
    }

    public static ProductVectorDocument dense(String model, int dims, float[] embedding, Map<String, Double> tfidf) {
        ProductVectorDocument doc = new ProductVectorDocument();
        doc.version = VERSION_DENSE;
        doc.model = model;
        doc.dims = dims;
        doc.embedding = embedding;
        doc.tfidf = tfidf;
        return doc;
    }

    public static ProductVectorDocument tfidfOnly(Map<String, Double> tfidf) {
        ProductVectorDocument doc = new ProductVectorDocument();
        doc.version = VERSION_TFIDF_ONLY;
        doc.tfidf = tfidf;
        return doc;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDims() {
        return dims;
    }

    public void setDims(int dims) {
        this.dims = dims;
    }

    public Map<String, Double> getTfidf() {
        return tfidf;
    }

    public void setTfidf(Map<String, Double> tfidf) {
        this.tfidf = tfidf;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public boolean hasDenseEmbedding() {
        return embedding != null && embedding.length > 0;
    }
}
