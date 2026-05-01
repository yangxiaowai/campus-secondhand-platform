package com.campus.recommend;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * 用户兴趣画像
 */
public class UserInterestProfile {

    private final Integer userId;
    private final Map<Integer, Double> categoryWeights;
    private final BigDecimal avgPrice;
    private final Map<String, Double> keywordWeights;

    public UserInterestProfile(Integer userId,
                               Map<Integer, Double> categoryWeights,
                               BigDecimal avgPrice,
                               Map<String, Double> keywordWeights) {
        this.userId = userId;
        this.categoryWeights = categoryWeights == null ? Collections.emptyMap() : categoryWeights;
        this.avgPrice = avgPrice;
        this.keywordWeights = keywordWeights == null ? Collections.emptyMap() : keywordWeights;
    }

    public Integer getUserId() {
        return userId;
    }

    public Map<Integer, Double> getCategoryWeights() {
        return categoryWeights;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public Map<String, Double> getKeywordWeights() {
        return keywordWeights;
    }
}

