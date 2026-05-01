package com.campus.service;

import com.campus.entity.Product;
import com.campus.recommend.RecommendationItem;

import java.util.List;

/**
 * 商品匹配引擎服务
 */
public interface ProductMatchService {

    /**
     * 处理商品发布事件：提取特征、计算匹配、推送收件箱
     */
    void processPublishedProduct(Product product);

    /**
     * 读取用户推荐收件箱
     */
    List<RecommendationItem> getInboxRecommendations(Integer userId, Integer limit);
}

