package com.campus.recommend;

import com.campus.entity.Product;

import java.util.Date;

/**
 * 推荐收件箱消息
 */
public class RecommendationItem {

    private final Integer userId;
    private final Product product;
    private final double score;
    private final Date createdAt;

    public RecommendationItem(Integer userId, Product product, double score) {
        this.userId = userId;
        this.product = product;
        this.score = score;
        this.createdAt = new Date();
    }

    public Integer getUserId() {
        return userId;
    }

    public Product getProduct() {
        return product;
    }

    public double getScore() {
        return score;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}

