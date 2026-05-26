package com.campus.search;

import com.campus.entity.Product;

/**
 * 带综合推荐分的商品（搜索列表重排结果）
 */
public class RankedProduct {

    private final Product product;
    private final double finalScore;
    private final double relevanceScore;
    private final double matchScore;
    private final double freshnessScore;
    private final double priceFitScore;

    public RankedProduct(Product product, double finalScore, double relevanceScore,
                         double matchScore, double freshnessScore, double priceFitScore) {
        this.product = product;
        this.finalScore = finalScore;
        this.relevanceScore = relevanceScore;
        this.matchScore = matchScore;
        this.freshnessScore = freshnessScore;
        this.priceFitScore = priceFitScore;
    }

    public Product getProduct() {
        return product;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public double getMatchScore() {
        return matchScore;
    }

    public double getFreshnessScore() {
        return freshnessScore;
    }

    public double getPriceFitScore() {
        return priceFitScore;
    }
}
