package com.campus.service;

import java.util.Map;

/**
 * 一键注入推荐/收件箱演示数据（验收用）
 */
public interface RecommendDemoSeedService {

    /**
     * 创建演示账号、养买家画像与索引，发布匹配商品并触发收件箱。
     */
    Map<String, Object> seedRecommendDemo();
}
