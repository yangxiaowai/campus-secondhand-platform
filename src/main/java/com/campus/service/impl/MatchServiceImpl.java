package com.campus.service.impl;

import com.campus.entity.UserProfile;
import com.campus.service.MatchService;
import com.campus.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 成员B：三维度匹配度
 * match = 0.5*category + 0.2*price + 0.3*keyword
 */
@Service
public class MatchServiceImpl implements MatchService {

    private static final double W_CATEGORY = 0.5;
    private static final double W_PRICE = 0.2;
    private static final double W_KEYWORD = 0.3;

    @Autowired
    private UserProfileService userProfileService;

    @Override
    public double computeMatchScore(Integer userId, Integer productCategoryId, Double productPrice,
                                    List<String> productKeywords) {
        if (userId == null) {
            return 0.0;
        }

        Map<Integer, Double> catNorm = userProfileService.getNormalizedCategoryWeights(userId);
        double categoryMatch = 0.0;
        if (productCategoryId != null && catNorm != null && !catNorm.isEmpty()) {
            categoryMatch = catNorm.getOrDefault(productCategoryId, 0.0);
        }

        double priceMatch = computePriceMatch(userId, productPrice);

        double keywordMatch = computeKeywordMatch(
                userProfileService.getKeywords(userId),
                productKeywords);

        return W_CATEGORY * categoryMatch + W_PRICE * priceMatch + W_KEYWORD * keywordMatch;
    }

    private double computePriceMatch(Integer userId, Double productPrice) {
        if (productPrice == null) {
            return 0.0;
        }
        UserProfile.PriceRange range = userProfileService.getPriceRange(userId);
        if (range == null) {
            return 0.5;
        }
        Double min = range.getMinPrice();
        Double max = range.getMaxPrice();
        if (min == null || max == null) {
            return 0.5;
        }
        // 无有效浏览价格区间（初始空画像）
        if (max <= 0 || min >= Double.MAX_VALUE / 2) {
            return 0.5;
        }
        if (min > max) {
            return 0.5;
        }
        if (productPrice >= min && productPrice <= max) {
            return 1.0;
        }
        double span = Math.max(max - min, 1.0);
        double dist;
        if (productPrice < min) {
            dist = min - productPrice;
        } else {
            dist = productPrice - max;
        }
        return 1.0 / (1.0 + dist / span);
    }

    /**
     * keyword_match = 交集大小 / 商品关键词总数（分工文档）
     */
    private double computeKeywordMatch(Map<String, Integer> userKeywords, List<String> productKeywords) {
        if (productKeywords == null || productKeywords.isEmpty()) {
            return 0.0;
        }
        if (userKeywords == null || userKeywords.isEmpty()) {
            return 0.0;
        }
        Set<String> userSet = new HashSet<>();
        for (String k : userKeywords.keySet()) {
            if (k != null && !k.isEmpty()) {
                userSet.add(k);
            }
        }
        int hit = 0;
        for (String pk : productKeywords) {
            if (pk == null || pk.isEmpty()) {
                continue;
            }
            if (userSet.contains(pk)) {
                hit++;
            }
        }
        return (double) hit / (double) productKeywords.size();
    }
}
