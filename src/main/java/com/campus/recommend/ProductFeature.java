package com.campus.recommend;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * 商品特征
 */
public class ProductFeature {

    private final Integer productId;
    private final Integer categoryId;
    private final BigDecimal price;
    private final Map<String, Double> keywordWeights;

    public ProductFeature(Integer productId, Integer categoryId, BigDecimal price, Map<String, Double> keywordWeights) {
        this.productId = productId;
        this.categoryId = categoryId;
        this.price = price;
        this.keywordWeights = keywordWeights == null ? Collections.emptyMap() : keywordWeights;
    }

    public Integer getProductId() {
        return productId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Map<String, Double> getKeywordWeights() {
        return keywordWeights;
    }
}

