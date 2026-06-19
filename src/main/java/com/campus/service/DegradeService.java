package com.campus.service;

import com.campus.entity.Product;

import java.util.List;
import java.util.Map;

/**
 * 成员D：降级容错服务
 *
 * 降级链路：
 * 正常 → Redis 画像/收件箱/个性化推荐
 * L1   → Redis 不可用，MySQL 画像 + 分类热门
 * L2   → 新用户冷启动，全局热门商品
 */
public interface DegradeService {

    /**
     * 检测 Redis 是否可用（用于 L1 降级判断）
     */
    boolean isRedisAvailable();

    /**
     * 带降级的个性化推荐（首页/推荐接口使用）
     */
    List<Product> recommendForUser(Integer userId, int limit);

    /**
     * 带降级的收件箱推荐（按匹配度排序；Redis 不可用时返回 L1/L2 兜底列表）
     */
    List<Map<String, Object>> inboxForUser(Integer userId, int limit);
}
