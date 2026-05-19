package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.dao.UserProfileMapper;
import com.campus.entity.Product;
import com.campus.entity.UserProfile;
import com.campus.service.DegradeService;
import com.campus.service.InboxService;
import com.campus.service.MetricsService;
import com.campus.service.RecommendService;
import com.campus.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 成员D：降级容错实现
 */
@Service
public class DegradeServiceImpl implements DegradeService {

    private static final Logger log = LoggerFactory.getLogger(DegradeServiceImpl.class);
    private static final String INBOX_KEY_PREFIX = "user:inbox:";

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private MetricsService metricsService;

    @Autowired(required = false)
    private InboxService inboxService;

    /** 压测时避免每次推荐都 PING，减轻业务连接池压力 */
    private static final long REDIS_CHECK_INTERVAL_MS = 2000L;
    private volatile long lastRedisCheckMs;
    private volatile boolean lastRedisAvailable;

    @Override
    public boolean isRedisAvailable() {
        if (redisTemplate == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastRedisCheckMs < REDIS_CHECK_INTERVAL_MS) {
            return lastRedisAvailable;
        }
        lastRedisAvailable = pingRedis();
        lastRedisCheckMs = now;
        return lastRedisAvailable;
    }

    private boolean pingRedis() {
        try {
            return Boolean.TRUE.equals(redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                String pong = connection.ping();
                return pong != null && pong.equalsIgnoreCase("PONG");
            }));
        } catch (Exception e) {
            log.warn("[成员D] Redis 不可用: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<Product> recommendForUser(Integer userId, int limit) {
        long start = System.currentTimeMillis();
        List<Product> result;
        try {
            if (userId == null) {
                metricsService.recordDegradeL2();
                result = l2HotProducts(limit);
            } else if (!isRedisAvailable()) {
                metricsService.recordDegradeL1();
                log.info("[成员D-L1降级] userId={}，Redis 不可用，走 MySQL 分类热门", userId);
                result = l1CategoryHotFromDb(userId, limit);
            } else if (isColdStart(userId, false)) {
                metricsService.recordDegradeL2();
                log.info("[成员D-L2降级] userId={}，冷启动用户，返回全局热门", userId);
                result = l2HotProducts(limit);
            } else {
                List<Product> inbox = loadProductsFromInbox(userId, limit);
                if (!inbox.isEmpty()) {
                    metricsService.recordCacheHit();
                    result = inbox;
                } else {
                    metricsService.recordCacheMiss();
                    result = getPersonalizedOrL1(userId, limit);
                }
            }
        } catch (Exception e) {
            log.error("[成员D] 推荐异常，执行 L1 兜底: {}", e.getMessage());
            metricsService.recordDegradeL1();
            result = userId == null ? l2HotProducts(limit) : l1CategoryHotFromDb(userId, limit);
        } finally {
            safeRecordRecommend(start);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> inboxForUser(Integer userId, int limit) {
        int safeLimit = limit <= 0 ? 20 : limit;
        if (userId == null) {
            return toInboxRows(l2HotProducts(safeLimit), 0.3, "L2");
        }
        if (!isRedisAvailable()) {
            metricsService.recordDegradeL1();
            return toInboxRows(l1CategoryHotFromDb(userId, safeLimit), 0.5, "L1");
        }
        if (isColdStart(userId, false)) {
            metricsService.recordDegradeL2();
            return toInboxRows(l2HotProducts(safeLimit), 0.3, "L2");
        }

        List<Map<String, Object>> rows;
        try {
            if (inboxService != null) {
                rows = new ArrayList<>(inboxService.listSorted(userId, safeLimit));
            } else {
                rows = loadInboxRowsLegacy(userId, safeLimit);
            }
        } catch (Exception e) {
            log.warn("[成员D] 收件箱读取失败，L1 兜底: {}", e.getMessage());
            metricsService.recordDegradeL1();
            return toInboxRows(l1CategoryHotFromDb(userId, safeLimit), 0.5, "L1");
        }
        if (!rows.isEmpty()) {
            metricsService.recordCacheHit();
            return rows;
        }
        metricsService.recordCacheMiss();
        try {
            return toInboxRows(recommendService.getPersonalizedRecommendations(userId, safeLimit), 0.4, "cb");
        } catch (Exception e) {
            log.warn("[成员D] 个性化推荐失败，L1 兜底: {}", e.getMessage());
            metricsService.recordDegradeL1();
            return toInboxRows(l1CategoryHotFromDb(userId, safeLimit), 0.5, "L1");
        }
    }

    private List<Product> getPersonalizedOrL1(Integer userId, int limit) {
        try {
            return recommendService.getPersonalizedRecommendations(userId, limit);
        } catch (Exception e) {
            log.warn("[成员D] 个性化推荐失败，L1 兜底: {}", e.getMessage());
            metricsService.recordDegradeL1();
            return l1CategoryHotFromDb(userId, limit);
        }
    }

    private void safeRecordRecommend(long start) {
        try {
            metricsService.recordRecommend(System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("[成员D] 指标记录失败: {}", e.getMessage());
        }
    }

    /** L1：从 MySQL 画像取偏好分类，再查该分类下按浏览量排序的商品 */
    private List<Product> l1CategoryHotFromDb(Integer userId, int limit) {
        Integer topCategory = resolveTopCategoryFromDb(userId, true);
        if (topCategory == null) {
            return l2HotProducts(limit);
        }
        List<Product> list = productMapper.findList(null, topCategory, 0);
        if (list == null || list.isEmpty()) {
            return l2HotProducts(limit);
        }
        list.sort((a, b) -> Integer.compare(
                b.getViewCount() == null ? 0 : b.getViewCount(),
                a.getViewCount() == null ? 0 : a.getViewCount()));
        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    /** L2：全局热门 */
    private List<Product> l2HotProducts(int limit) {
        List<Product> hot = productMapper.findHotProducts(limit);
        return hot == null ? Collections.emptyList() : hot;
    }

    /**
     * @param mysqlOnly true 时仅查 MySQL（L1 降级，避免再访问 Redis）
     */
    private Integer resolveTopCategoryFromDb(Integer userId, boolean mysqlOnly) {
        if (!mysqlOnly) {
            try {
                UserProfile fromService = userProfileService.getProfile(userId);
                if (fromService != null && fromService.getCategoryWeights() != null
                        && !fromService.getCategoryWeights().isEmpty()) {
                    return fromService.getCategoryWeights().entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(null);
                }
            } catch (Exception e) {
                log.warn("[成员D] Redis 画像读取失败，改用 MySQL: {}", e.getMessage());
            }
        }
        try {
            UserProfile fromDb = userProfileMapper.findByUserId(userId);
            if (fromDb != null && fromDb.getCategoryWeights() != null && !fromDb.getCategoryWeights().isEmpty()) {
                return fromDb.getCategoryWeights().entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("[成员D] MySQL 画像读取失败: {}", e.getMessage());
        }
        return null;
    }

    private boolean isColdStart(Integer userId, boolean mysqlOnly) {
        UserProfile profile;
        if (mysqlOnly) {
            profile = userProfileMapper.findByUserId(userId);
            if (profile == null) {
                profile = new UserProfile();
                profile.setUserId(userId);
            }
        } else {
            try {
                profile = userProfileService.getProfile(userId);
            } catch (Exception e) {
                profile = userProfileMapper.findByUserId(userId);
                if (profile == null) {
                    return true;
                }
            }
        }
        boolean noCategory = profile.getCategoryWeights() == null || profile.getCategoryWeights().isEmpty();
        boolean noKeywords = profile.getKeywords() == null || profile.getKeywords().isEmpty();
        boolean noBrowse = profile.getBrowseCount() == null || profile.getBrowseCount() <= 0;
        return noCategory && noKeywords && noBrowse;
    }

    private List<Map<String, Object>> loadInboxRowsLegacy(Integer userId, int limit) {
        String inboxKey = INBOX_KEY_PREFIX + userId;
        Set<ZSetOperations.TypedTuple<Object>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(inboxKey, 0, limit - 1);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (tuples == null) {
            return rows;
        }
        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> t : tuples) {
            if (t.getValue() == null || t.getScore() == null) {
                continue;
            }
            Integer productId = parseInt(t.getValue());
            if (productId == null) {
                continue;
            }
            Product p = productMapper.findById(productId);
            if (p == null || Objects.equals(p.getStatus(), 3)) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("rank", rank++);
            row.put("productId", productId);
            row.put("matchScore", round3(t.getScore()));
            row.put("name", p.getName());
            row.put("price", p.getPrice());
            row.put("categoryId", p.getCategoryId());
            row.put("imageUrl", p.getImageUrl());
            row.put("source", "inbox");
            rows.add(row);
        }
        return rows;
    }

    private List<Product> loadProductsFromInbox(Integer userId, int limit) {
        if (redisTemplate == null) {
            return Collections.emptyList();
        }
        try {
            String inboxKey = INBOX_KEY_PREFIX + userId;
            Set<Object> ids = redisTemplate.opsForZSet().reverseRange(inboxKey, 0, limit - 1);
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }
            List<Product> result = new ArrayList<>();
            for (Object raw : ids) {
                Integer pid = parseInt(raw);
                if (pid == null) {
                    continue;
                }
                Product p = productMapper.findById(pid);
                if (p != null && !Objects.equals(p.getStatus(), 3)) {
                    result.add(p);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[成员D] 收件箱加载失败: {}", e.getMessage());
            metricsService.recordDegradeL1();
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> toInboxRows(List<Product> products, double defaultScore, String source) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (products == null) {
            return rows;
        }
        for (Product p : products) {
            if (p == null || p.getId() == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("productId", p.getId());
            row.put("matchScore", defaultScore);
            row.put("name", p.getName());
            row.put("price", p.getPrice());
            row.put("categoryId", p.getCategoryId());
            row.put("imageUrl", p.getImageUrl());
            row.put("source", source);
            rows.add(row);
        }
        return rows;
    }

    private static Integer parseInt(Object raw) {
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
