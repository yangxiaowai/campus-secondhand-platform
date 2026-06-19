package com.campus.service;

import com.campus.entity.Product;
import com.campus.search.SearchMode;

import java.util.List;
import java.util.Map;

/**
 * 商品搜索索引：分布式倒排索引 + TF-IDF 语义向量
 */
public interface ProductSearchIndexService {

    void indexProduct(Product product);

    void removeProduct(Integer productId);

    void rebuildAllIndexes();

    /**
     * Scatter-Gather 检索，返回 productId -> 相关度分数
     */
    Map<Integer, Double> search(String keyword, Integer categoryId, SearchMode mode);

    /** 最近一次语义检索使用的引擎（redis-stack-knn / search:vec-cosine / tfidf-fallback） */
    String getLastSemanticEngine();
}
