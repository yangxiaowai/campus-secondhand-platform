package com.campus.service;

import com.campus.entity.Product;

import java.util.List;

/**
 * 推荐服务接口
 * 成员4第一阶段：推荐服务改造 + 收件箱雏形
 * - 推荐结果写入Redis List（rec:inbox:{userId}）
 * - 用户访问推荐接口时，从Redis List读取
 * - 初始化测试数据
 */
public interface RecommendService {

    List<Product> getSimilarProducts(Integer productId, Integer limit);

    List<Product> getPersonalizedRecommendations(Integer userId, Integer limit);

    void recordBrowseHistory(Integer userId, Integer productId);

    List<Product> getBrowseHistory(Integer userId, Integer limit);

    /**
     * 推送推荐商品到用户收件箱
     * Redis结构：List，Key: rec:inbox:{userId}
     *
     * @param userId    用户ID
     * @param productId 商品ID
     */
    void pushToInbox(Integer userId, Integer productId);

    /**
     * 从用户收件箱读取推荐商品
     *
     * @param userId 用户ID
     * @param limit  数量限制
     * @return 推荐商品列表
     */
    List<Product> readInbox(Integer userId, Integer limit);

    /**
     * 初始化测试数据
     * 往Redis收件箱里塞几条测试推荐数据
     */
    void initTestData();
}
