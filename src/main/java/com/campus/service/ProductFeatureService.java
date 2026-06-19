package com.campus.service;

import com.campus.entity.Product;

import java.util.List;

/**
 * 成员B：商品特征提取（中文分词 + Redis 特征存储）
 */
public interface ProductFeatureService {

    /**
     * 商品标题分词（含停用词过滤前的原始词片，用于调试打印）
     */
    List<String> tokenizeTitle(String title);

    /**
     * 提取关键词：分词 → 去停用词 → 去空/单字噪声
     */
    List<String> extractKeywords(String title);

    /**
     * 将分类、价格、关键词写入 Redis Hash：product:feature:{productId}
     */
    void saveProductFeatures(Product product, List<String> keywords);

    /**
     * 删除商品特征缓存（商品删除时可调用，可选）
     */
    void deleteProductFeatures(Integer productId);
}
