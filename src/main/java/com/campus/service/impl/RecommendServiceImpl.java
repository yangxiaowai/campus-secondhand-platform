package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.service.RecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 推荐服务实现类
 * 技术亮点：
 * 1. 基于内容的推荐算法（Content-Based Filtering）
 * 2. 使用内存缓存存储浏览历史（生产环境建议使用Redis）
 * 3. LRU策略限制历史记录数量，防止内存溢出
 */
@Service
public class RecommendServiceImpl implements RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);

    @Autowired
    private ProductMapper productMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    // 用户浏览历史缓存（userId -> 浏览的商品ID列表，按时间倒序）
    private static final Map<Integer, LinkedList<Integer>> browseHistoryCache = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY_SIZE = 50;
    private static final int PROFILE_TTL_SECONDS = 12 * 60 * 60;
    private static final int HISTORY_TTL_SECONDS = 24 * 60 * 60;
    private static final int PROFILE_LOCK_TTL_SECONDS = 5;

    @Override
    public List<Product> getSimilarProducts(Integer productId, Integer limit) {
        Product currentProduct = productMapper.findById(productId);
        if (currentProduct == null) {
            return Collections.emptyList();
        }

        List<Product> sameCategory = productMapper.findList(null, currentProduct.getCategoryId(), 0);
        if (sameCategory == null) {
            return Collections.emptyList();
        }

        List<Product> result = new ArrayList<>();
        for (Product p : sameCategory) {
            if (!p.getId().equals(productId)) {
                result.add(p);
            }
        }

        result.sort((a, b) -> {
            int va = a.getViewCount() == null ? 0 : a.getViewCount();
            int vb = b.getViewCount() == null ? 0 : b.getViewCount();
            return Integer.compare(vb, va);
        });

        if (result.size() > limit) {
            result = result.subList(0, limit);
        }

        return result;
    }

    @Override
    public List<Product> getPersonalizedRecommendations(Integer userId, Integer limit) {
        Map<Integer, Integer> categoryCount = loadOrRebuildCategoryProfile(userId);
        LinkedList<Integer> history = loadHistory(userId);
        if (history == null || history.isEmpty()) {
            List<Product> hot = productMapper.findHotProducts(limit);
            return hot == null ? Collections.emptyList() : hot;
        }

        List<Map.Entry<Integer, Integer>> sortedCategories = new ArrayList<>(categoryCount.entrySet());
        sortedCategories.sort((a, b) -> b.getValue() - a.getValue());

        Set<Integer> historySet = new HashSet<>(history);
        List<Product> recommendations = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : sortedCategories) {
            if (recommendations.size() >= limit) break;

            List<Product> categoryProducts = productMapper.findList(null, entry.getKey(), 0);
            if (categoryProducts == null) {
                continue;
            }
            for (Product p : categoryProducts) {
                if (!historySet.contains(p.getId())) {
                    recommendations.add(p);
                    if (recommendations.size() >= limit) break;
                }
            }
        }

        if (recommendations.size() < limit) {
            List<Product> hotProducts = productMapper.findHotProducts(limit);
            if (hotProducts != null) {
                for (Product p : hotProducts) {
                    if (!historySet.contains(p.getId()) && !containsProduct(recommendations, p.getId())) {
                        recommendations.add(p);
                        if (recommendations.size() >= limit) break;
                    }
                }
            }
        }

        return recommendations;
    }

    @Override
    public void recordBrowseHistory(Integer userId, Integer productId) {
        if (userId == null || productId == null) return;

        String lockKey = "lock:profile:update:" + userId;
        String lockValue = UUID.randomUUID().toString();
        if (!acquireLock(lockKey, lockValue, PROFILE_LOCK_TTL_SECONDS)) {
            return;
        }

        try {
            browseHistoryCache.compute(userId, (key, history) -> {
                if (history == null) {
                    history = new LinkedList<>();
                }
                history.remove(productId);
                history.addFirst(productId);
                while (history.size() > MAX_HISTORY_SIZE) {
                    history.removeLast();
                }
                return history;
            });
            syncHistoryToRedis(userId);
            rebuildProfileCache(userId);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    @Override
    public List<Product> getBrowseHistory(Integer userId, Integer limit) {
        LinkedList<Integer> history = browseHistoryCache.get(userId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }

        List<Product> result = new ArrayList<>();
        int count = 0;
        for (Integer productId : history) {
            if (count >= limit) break;
            Product p = productMapper.findById(productId);
            if (p != null) {
                result.add(p);
                count++;
            }
        }

        return result;
    }

    private boolean containsProduct(List<Product> list, Integer productId) {
        for (Product p : list) {
            if (p.getId().equals(productId)) return true;
        }
        return false;
    }

    private String profileKey(Integer userId) {
        return "rec:profile:" + userId;
    }

    private String historyKey(Integer userId) {
        return "rec:history:" + userId;
    }

    private void syncHistoryToRedis(Integer userId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            LinkedList<Integer> history = browseHistoryCache.get(userId);
            if (history == null || history.isEmpty()) {
                return;
            }
            String key = historyKey(userId);
            redisTemplate.delete(key);
            for (Integer productId : history) {
                redisTemplate.opsForList().rightPush(key, productId);
            }
            redisTemplate.expire(key, HISTORY_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("[推荐] Redis 同步浏览历史失败: {}", e.getMessage());
        }
    }

    private LinkedList<Integer> loadHistory(Integer userId) {
        LinkedList<Integer> local = browseHistoryCache.get(userId);
        if (local != null && !local.isEmpty()) {
            return local;
        }
        if (redisTemplate == null) {
            return local;
        }
        try {
            List<Object> values = redisTemplate.opsForList().range(historyKey(userId), 0, MAX_HISTORY_SIZE - 1);
            if (values == null || values.isEmpty()) {
                return local;
            }
            LinkedList<Integer> fromRedis = new LinkedList<>();
            for (Object value : values) {
                try {
                    fromRedis.add(Integer.parseInt(String.valueOf(value)));
                } catch (Exception ignored) {
                }
            }
            if (!fromRedis.isEmpty()) {
                browseHistoryCache.put(userId, fromRedis);
            }
            return fromRedis;
        } catch (DataAccessException e) {
            log.warn("[推荐] Redis 读取浏览历史失败，使用本地缓存: {}", e.getMessage());
            return local;
        }
    }

    private Map<Integer, Integer> loadOrRebuildCategoryProfile(Integer userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        if (redisTemplate != null) {
            try {
                Map<Object, Object> cached = redisTemplate.opsForHash().entries(profileKey(userId));
                Map<Integer, Integer> profile = convertProfile(cached);
                if (!profile.isEmpty()) {
                    redisTemplate.expire(profileKey(userId), PROFILE_TTL_SECONDS, TimeUnit.SECONDS);
                    return profile;
                }
            } catch (DataAccessException e) {
                log.warn("[推荐] Redis 读取画像失败，从本地历史重建: {}", e.getMessage());
            }
        }
        return rebuildProfileCache(userId);
    }

    private Map<Integer, Integer> rebuildProfileCache(Integer userId) {
        LinkedList<Integer> history = loadHistory(userId);
        Map<Integer, Integer> categoryCount = new HashMap<>();
        if (history != null) {
            for (Integer productId : history) {
                Product p = productMapper.findById(productId);
                if (p != null && p.getCategoryId() != null) {
                    Integer categoryId = p.getCategoryId();
                    Integer current = categoryCount.get(categoryId);
                    categoryCount.put(categoryId, current == null ? 1 : current + 1);
                }
            }
        }
        if (redisTemplate != null && !categoryCount.isEmpty()) {
            try {
                String key = profileKey(userId);
                redisTemplate.delete(key);
                Map<String, Integer> store = new HashMap<>();
                for (Map.Entry<Integer, Integer> e : categoryCount.entrySet()) {
                    store.put(String.valueOf(e.getKey()), e.getValue());
                }
                redisTemplate.opsForHash().putAll(key, store);
                redisTemplate.expire(key, PROFILE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (DataAccessException e) {
                log.warn("[推荐] Redis 写入画像失败: {}", e.getMessage());
            }
        }
        return categoryCount;
    }

    private Map<Integer, Integer> convertProfile(Map<Object, Object> cached) {
        Map<Integer, Integer> result = new HashMap<>();
        if (cached == null || cached.isEmpty()) {
            return result;
        }
        for (Map.Entry<Object, Object> entry : cached.entrySet()) {
            try {
                Integer categoryId = Integer.parseInt(String.valueOf(entry.getKey()));
                Integer weight = Integer.parseInt(String.valueOf(entry.getValue()));
                result.put(categoryId, weight);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private boolean acquireLock(String lockKey, String lockValue, int ttlSeconds) {
        if (redisTemplate == null) {
            return true;
        }
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (DataAccessException e) {
            log.warn("[推荐] Redis 获取锁失败，继续无锁执行: {}", e.getMessage());
            return true;
        }
    }

    private void releaseLock(String lockKey, String lockValue) {
        if (redisTemplate == null) {
            return;
        }
        try {
            Object current = redisTemplate.opsForValue().get(lockKey);
            if (current != null && lockValue.equals(String.valueOf(current))) {
                redisTemplate.delete(lockKey);
            }
        } catch (DataAccessException e) {
            log.warn("[推荐] Redis 释放锁失败: {}", e.getMessage());
        }
    }
}
