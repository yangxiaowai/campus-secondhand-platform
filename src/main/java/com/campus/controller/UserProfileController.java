package com.campus.controller;

import com.campus.common.Result;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.entity.UserProfile;
import com.campus.service.ProductService;
import com.campus.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户兴趣画像控制器
 * 
 * 成员A：用户兴趣画像系统
 * 
 * 功能：
 *   1. 提供画像可视化接口，返回当前用户的完整画像JSON
 *   2. 提供画像调试接口，方便开发和验收
 * 
 * 验收方法：
 *   1. 登录后访问 /user/profile → 返回完整画像JSON ✅
 *   2. 浏览几个商品后再次访问 → 画像数据已更新 ✅
 *   3. 用Redis客户端查看 user:profile:{userId} → 数据一致 ✅
 */
@Controller
@RequestMapping("/user")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    @Autowired
    private UserProfileService userProfileService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ProductService productService;

    private static final String INBOX_KEY_PREFIX = "user:inbox:";
    /**
     * 获取当前登录用户的兴趣画像
     * 
     * 返回完整的画像数据，包括：
     *   - categoryWeights: 分类偏好权重
     *   - normalizedWeights: 归一化后的分类权重（总和=1）
     *   - priceRange: 价格偏好区间
     *   - keywords: 关键词偏好
     *   - browseCount: 浏览总数
     *   - purchaseCount: 购买次数
     * 
     * 给成员B使用：成员B可以通过此接口验证画像数据是否正确
     */
    @RequestMapping("/profile")
    @ResponseBody
    public Result<Map<String, Object>> getProfile(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }

        UserProfile profile = userProfileService.getProfile(user.getId());
        Map<String, Object> data = new HashMap<>();

        // 原始分类权重
        data.put("categoryWeights", profile.getCategoryWeights());

        // 归一化后的分类权重（成员B匹配引擎使用的数据）
        data.put("normalizedWeights", userProfileService.getNormalizedCategoryWeights(user.getId()));

        // 价格区间
        UserProfile.PriceRange priceRange = profile.getPriceRange();
        Map<String, Object> priceMap = new HashMap<>();
        priceMap.put("min", priceRange.getMinPrice());
        priceMap.put("max", priceRange.getMaxPrice());
        priceMap.put("avg", priceRange.getAvgPrice());
        data.put("priceRange", priceMap);

        // 关键词偏好
        data.put("keywords", profile.getKeywords());

        // 活跃度指标
        data.put("browseCount", profile.getBrowseCount());
        data.put("purchaseCount", profile.getPurchaseCount());
        data.put("lastBrowseTime", profile.getLastBrowseTime());
        data.put("version", profile.getVersion());

        log.info("用户画像查询成功，userId={}, 分类数={}, 关键词数={}",
                user.getId(),
                profile.getCategoryWeights().size(),
                profile.getKeywords().size());

        return Result.success("画像查询成功", data);
    }

    /**
     * 手动触发当前用户画像重建
     * 用于调试和验收
     */
    @RequestMapping("/profile/rebuild")
    @ResponseBody
    public Result<String> rebuildProfile(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }

        userProfileService.rebuildProfile(user.getId());
        return Result.success("画像重建完成，请访问 /user/profile 查看");
    }

    /**
     * 获取画像的简要摘要（用于首页展示）
     * 返回用户最感兴趣的分类名称和关键词
     */
    @RequestMapping("/profile/summary")
    @ResponseBody
    public Result<Map<String, Object>> getProfileSummary(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }

        UserProfile profile = userProfileService.getProfile(user.getId());
        Map<String, Object> summary = new HashMap<>();

        // 找出权重最高的分类
        Map<Integer, Integer> catWeights = profile.getCategoryWeights();
        if (catWeights != null && !catWeights.isEmpty()) {
            Map.Entry<Integer, Integer> topCategory = catWeights.entrySet()
                    .stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            if (topCategory != null) {
                summary.put("topCategoryId", topCategory.getKey());
                summary.put("topCategoryWeight", topCategory.getValue());
            }
        }

        // 找出频次最高的前5个关键词
        Map<String, Integer> keywords = profile.getKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            String[] topKeywords = keywords.entrySet()
                    .stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .toArray(String[]::new);
            summary.put("topKeywords", topKeywords);
        }

        summary.put("browseCount", profile.getBrowseCount());
        summary.put("purchaseCount", profile.getPurchaseCount());

        return Result.success(summary);
    }

    /**
     * 成员B：推荐收件箱（Redis ZSet，按匹配度降序）
     * 用于验收「发布后目标用户收件箱出现该商品」
     */
    @RequestMapping("/inbox")
    @ResponseBody
    public Result<List<Map<String, Object>>> getRecommendInbox(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        if (redisTemplate == null) {
            return Result.error(503, "Redis 未配置，无法读取收件箱");
        }
        String inboxKey = INBOX_KEY_PREFIX + user.getId();
        Set<ZSetOperations.TypedTuple<Object>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(inboxKey, 0, 99);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<Object> t : tuples) {
                if (t.getValue() == null || t.getScore() == null) {
                    continue;
                }
                Integer productId = parseProductId(t.getValue());
                if (productId == null) {
                    continue;
                }
                Product p = productService.findById(productId);
                if (p == null || (p.getStatus() != null && p.getStatus() == 3)) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("productId", productId);
                row.put("matchScore", round3(t.getScore()));
                row.put("name", p.getName());
                row.put("price", p.getPrice());
                row.put("categoryId", p.getCategoryId());
                row.put("imageUrl", p.getImageUrl());
                rows.add(row);
            }
        }
        return Result.success("推荐收件箱", rows);
    }

    private static Integer parseProductId(Object raw) {
        if (raw instanceof Integer) {
            return (Integer) raw;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
