package com.campus.service;

import com.campus.entity.UserProfile;

import java.util.Map;

/**
 * 用户兴趣画像服务接口
 * 
 * 成员A：用户兴趣画像系统
 * 
 * ========== 功能概述 ==========
 * 为每个用户构建多维兴趣画像，包含：
 *   1. 分类偏好权重（浏览过哪些分类的商品，各多少次）
 *   2. 价格偏好区间（浏览过的商品价格范围）
 *   3. 关键词偏好（浏览过的商品标题中提取的关键词频次）
 *   4. 活跃度指标（浏览次数、购买次数、最近浏览时间）
 * 
 * 画像数据同时存储在 Redis（Hash，高性能读写）和 MySQL（JSON，持久化备份）中。
 * 
 * ========== 给成员B（匹配引擎）的接口说明 ==========
 * 成员B在实现匹配度计算时，需要通过以下方法获取用户画像数据：
 * 
 *   方法                                   返回值                    用途
 *   ─────────────────────────────────────────────────────────────────────
 *   getProfile(userId)                    UserProfile              获取完整画像（含所有维度）
 *   getCategoryWeights(userId)            Map<Integer, Integer>    分类匹配度计算（权重0.5）
 *   getPriceRange(userId)                 UserProfile.PriceRange   价格匹配度计算（权重0.2）
 *   getKeywords(userId)                   Map<String, Integer>     关键词匹配度计算（权重0.3）
 *   getNormalizedCategoryWeights(userId)  Map<Integer, Double>     归一化后的分类权重（总和=1）
 * 
 *   使用示例（成员B的 MatchService 中）：
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │ // 获取用户画像数据                                             │
 *   │ Map<Integer, Double> catWeights = userProfileService           │
 *   │     .getNormalizedCategoryWeights(userId);                     │
 *   │ UserProfile.PriceRange priceRange = userProfileService         │
 *   │     .getPriceRange(userId);                                    │
 *   │ Map<String, Integer> keywords = userProfileService             │
 *   │     .getKeywords(userId);                                      │
 *   │                                                                │
 *   │ // 计算三维度匹配度                                            │
 *   │ double categoryScore = catWeights                              │
 *   │     .getOrDefault(product.getCategoryId(), 0.0);               │
 *   │ double priceScore = priceRange.contains(product.getPrice())    │
 *   │     ? 1.0 : 0.5;                                              │
 *   │ double keywordScore = calculateKeywordMatch(                   │
 *   │     productKeywords, keywords);                                │
 *   │                                                                │
 *   │ double matchScore = 0.5 * categoryScore                        │
 *   │                  + 0.2 * priceScore                            │
 *   │                  + 0.3 * keywordScore;                         │
 *   └─────────────────────────────────────────────────────────────────┘
 */
public interface UserProfileService {

    // ==================== 成员B使用的查询接口 ====================

    /**
     * 获取用户完整画像
     * 
     * 给成员B使用：获取用户的所有画像维度数据
     * 
     * @param userId 用户ID
     * @return 完整画像对象，如果用户没有画像数据则返回空画像（不会返回null）
     */
    UserProfile getProfile(Integer userId);

    /**
     * 获取用户分类偏好权重（原始频次）
     * 
     * 给成员B使用：用于分类匹配度计算
     * 返回 Map<分类ID, 浏览频次>
     * 
     * @param userId 用户ID
     * @return 分类ID → 浏览频次，无数据返回空Map
     */
    Map<Integer, Integer> getCategoryWeights(Integer userId);

    /**
     * 获取用户归一化后的分类权重（总和为1.0）
     * 
     * 给成员B使用：推荐使用此方法计算分类匹配度
     * 归一化公式：weight_norm(c) = weight(c) / Σ weight(all_categories)
     * 
     * @param userId 用户ID
     * @return 分类ID → 归一化权重(0~1)，无数据返回空Map
     */
    Map<Integer, Double> getNormalizedCategoryWeights(Integer userId);

    /**
     * 获取用户价格偏好区间
     * 
     * 给成员B使用：用于价格匹配度计算
     * 
     * @param userId 用户ID
     * @return 价格区间对象（含min/max/avg），无数据返回默认区间
     */
    UserProfile.PriceRange getPriceRange(Integer userId);

    /**
     * 获取用户关键词偏好（频次）
     * 
     * 给成员B使用：用于关键词匹配度计算
     * 返回 Map<关键词, 出现频次>
     * 
     * @param userId 用户ID
     * @return 关键词 → 频次，无数据返回空Map
     */
    Map<String, Integer> getKeywords(Integer userId);

    // ==================== 画像更新接口 ====================

    /**
     * 记录用户浏览商品行为 → 增量更新画像
     * 
     * 当用户浏览商品时调用此方法，自动更新：
     *   1. 该商品分类的权重 +1
     *   2. 扩展价格区间
     *   3. 商品标题关键词频次 +1
     *   4. 更新最近浏览时间
     *   5. 浏览总数 +1
     * 
     * @param userId    用户ID
     * @param categoryId 商品分类ID
     * @param price     商品价格
     * @param keywords  商品标题关键词列表（由成员B的分词服务提供）
     */
    void recordBrowse(Integer userId, Integer categoryId, Double price, java.util.List<String> keywords);

    /**
     * 记录用户购买商品行为 → 增量更新画像（购买权重更高）
     * 
     * 购买比浏览的权重更高（+3 vs +1），因为购买行为更能反映用户兴趣。
     * 
     * @param userId    用户ID
     * @param categoryId 商品分类ID
     * @param price     商品价格
     * @param keywords  商品标题关键词列表
     */
    void recordPurchase(Integer userId, Integer categoryId, Double price, java.util.List<String> keywords);

    /**
     * 记录用户登录 → 更新在线状态和最后登录时间
     * 
     * @param userId 用户ID
     */
    void recordLogin(Integer userId);

    // ==================== 画像管理接口 ====================

    /**
     * 全量重建用户画像（定时任务使用）
     * 
     * 从浏览历史表中扫描所有用户的浏览记录，重新计算完整画像。
     * 建议每天凌晨执行一次。
     * 
     * @param userId 用户ID
     * @return 重建后的完整画像
     */
    UserProfile rebuildProfile(Integer userId);

    /**
     * 全量重建所有用户的画像
     * 定时任务使用，遍历所有有浏览记录的用户重建画像
     */
    void rebuildAllProfiles();

    /**
     * 从 MySQL 恢复画像到 Redis
     * 当 Redis 缓存丢失时调用，从数据库恢复
     * 
     * @param userId 用户ID
     * @return 恢复成功返回true
     */
    boolean restoreProfileFromDb(Integer userId);

    /**
     * 删除用户画像
     * 
     * @param userId 用户ID
     */
    void deleteProfile(Integer userId);
}
