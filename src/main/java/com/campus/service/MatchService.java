package com.campus.service;

import java.util.List;

/**
 * 成员B：用户画像与商品特征的三维度匹配度计算
 *
 * match_score = 0.5 × category_match + 0.2 × price_match + 0.3 × keyword_match
 */
public interface MatchService {

    /**
     * @param userId           用户ID
     * @param productCategoryId 商品分类ID
     * @param productPrice     商品价格
     * @param productKeywords  商品标题关键词（与画像侧一致的分词结果）
     * @return 0~1 的综合匹配分
     */
    double computeMatchScore(Integer userId, Integer productCategoryId, Double productPrice,
                           List<String> productKeywords);
}
