package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.service.RecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RecommendServiceImpl implements RecommendService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendServiceImpl.class);

    @Autowired
    private ProductMapper productMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final Map<Integer, LinkedList<Integer>> browseHistoryCache = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY_SIZE = 50;
    private static final int PROFILE_TTL_SECONDS = 12 * 60 * 60;
    private static final int HISTORY_TTL_SECONDS = 24 * 60 * 60;
    private static final int PROFILE_LOCK_TTL_SECONDS = 5;
    private static final int INBOX_TTL_SECONDS = 24 * 60 * 60;

    @Override
    public List<Product> getSimilarProducts(Integer productId, Integer limit) {
        Product currentProduct = productMapper.findById(productId);
        if (currentProduct == null) {
            return Collections.emptyList();
        }

        List<Product> sameCategory = productMapper.findList(null, currentProduct.getCategoryId(), 0);

        List<Product> result = new ArrayList<>();
        for (Product p : sameCategory) {
            if (!p.getId().equals(productId)) {
                result.add(p);
            }
        }

        result.sort((a, b) -> b.getViewCount() - a.getViewCount());

        if (result.size() > limit) {
            result = result.subList(0, limit);
        }

        return result;
    }

    @Override
    public List<Product> getPersonalizedRecommendations(Integer userId, Integer limit) {
        List<Product> inboxProducts = readInbox(userId, limit);
        if (!inboxProducts.isEmpty()) {
            return inboxProducts;
        }

        Map<Integer, Integer> categoryCount = loadOrRebuildCategoryProfile(userId);
        LinkedList<Integer> history = loadHistory(userId);
        if (history == null || history.isEmpty()) {
            return productMapper.findHotProducts(limit);
        }

        List<Map.Entry<Integer, Integer>> sortedCategories = new ArrayList<>(categoryCount.entrySet());
        sortedCategories.sort((a, b) -> b.getValue() - a.getValue());

        Set<Integer> historySet = new HashSet<>(history);
        List<Product> recommendations = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : sortedCategories) {
            if (recommendations.size() >= limit) break;

            List<Product> categoryProducts = productMapper.findList(null, entry.getKey(), 0);
            for (Product p : categoryProducts) {
                if (!historySet.contains(p.getId())) {
                    recommendations.add(p);
                    if (recommendations.size() >= limit) break;
                }
            }
        }

        if (recommendations.size() < limit) {
            List<Product> hotProducts = productMapper.findHotProducts(limit);
            for (Product p : hotProducts) {
                if (!historySet.contains(p.getId()) && !containsProduct(recommendations, p.getId())) {
                    recommendations.add(p);
                    if (recommendations.size() >= limit) break;
                }
            }
        }

        for (Product p : recommendations) {
            pushToInbox(userId, p.getId());
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

    // ==================== 第一阶段：推荐收件箱 ====================

    @Override
    public void pushToInbox(Integer userId, Integer productId) {
        if (userId == null || productId == null) return;
        if (redisTemplate == null) {
            logger.warn("RedisTemplate 未注入，无法写入收件箱 userId={}, productId={}", userId, productId);
            return;
        }
        String key = inboxKey(userId);
        redisTemplate.opsForList().leftPush(key, productId);
        redisTemplate.expire(key, INBOX_TTL_SECONDS, TimeUnit.SECONDS);
        logger.debug("推送推荐到收件箱: userId={}, productId={}", userId, productId);
    }

    @Override
    public List<Product> readInbox(Integer userId, Integer limit) {
        if (userId == null) return Collections.emptyList();
        if (redisTemplate == null) {
            logger.warn("RedisTemplate 未注入，无法读取收件箱 userId={}", userId);
            return Collections.emptyList();
        }
        String key = inboxKey(userId);
        List<Object> productIdObjs = redisTemplate.opsForList().range(key, 0, limit - 1);
        if (productIdObjs == null || productIdObjs.isEmpty()) {
            return Collections.emptyList();
        }

        List<Product> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Object obj : productIdObjs) {
            try {
                Integer pid = Integer.parseInt(String.valueOf(obj));
                if (seen.contains(pid)) continue;
                seen.add(pid);
                Product p = productMapper.findById(pid);
                if (p != null) {
                    result.add(p);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Override
    public void initTestData() {
        if (redisTemplate == null) {
            logger.warn("RedisTemplate 未注入，无法初始化测试数据");
            return;
        }

        int testUserId = 1;
        String key = inboxKey(testUserId);

        if (redisTemplate.opsForList().size(key) > 0) {
            logger.info("收件箱已有数据，跳过初始化: key={}", key);
            return;
        }

        // 直接写入测试商品ID到Redis，模拟推荐算法的推荐结果
        List<Integer> testProductIds = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        for (Integer productId : testProductIds) {
            redisTemplate.opsForList().leftPush(key, productId);
        }
        redisTemplate.expire(key, INBOX_TTL_SECONDS, TimeUnit.SECONDS);

        logger.info("测试数据初始化完成: userId={}, 推送{}条测试推荐到收件箱 key={}", testUserId, testProductIds.size(), key);
    }

    // ==================== 私有方法 ====================

    private String inboxKey(Integer userId) {
        return "rec:inbox:" + userId;
    }

    private String profileKey(Integer userId) {
        return "rec:profile:" + userId;
    }

    private String historyKey(Integer userId) {
        return "rec:history:" + userId;
    }

    private boolean containsProduct(List<Product> list, Integer productId) {
        for (Product p : list) {
            if (p.getId().equals(productId)) return true;
        }
        return false;
    }

    private void syncHistoryToRedis(Integer userId) {
        if (redisTemplate == null) return;
        LinkedList<Integer> history = browseHistoryCache.get(userId);
        if (history == null || history.isEmpty()) return;
        String key = historyKey(userId);
        redisTemplate.delete(key);
        for (Integer productId : history) {
            redisTemplate.opsForList().rightPush(key, productId);
        }
        redisTemplate.expire(key, HISTORY_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private LinkedList<Integer> loadHistory(Integer userId) {
        LinkedList<Integer> local = browseHistoryCache.get(userId);
        if (local != null && !local.isEmpty()) return local;
        if (redisTemplate == null) return local;
        List<Object> values = redisTemplate.opsForList().range(historyKey(userId), 0, MAX_HISTORY_SIZE - 1);
        if (values == null || values.isEmpty()) return local;
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
    }

    private Map<Integer, Integer> loadOrRebuildCategoryProfile(Integer userId) {
        if (userId == null) return Collections.emptyMap();
        if (redisTemplate != null) {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(profileKey(userId));
            Map<Integer, Integer> profile = convertProfile(cached);
            if (!profile.isEmpty()) {
                redisTemplate.expire(profileKey(userId), PROFILE_TTL_SECONDS, TimeUnit.SECONDS);
                return profile;
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
        if (redisTemplate != null) {
            String key = profileKey(userId);
            redisTemplate.delete(key);
            if (!categoryCount.isEmpty()) {
                Map<String, Integer> store = new HashMap<>();
                for (Map.Entry<Integer, Integer> e : categoryCount.entrySet()) {
                    store.put(String.valueOf(e.getKey()), e.getValue());
                }
                redisTemplate.opsForHash().putAll(key, store);
                redisTemplate.expire(key, PROFILE_TTL_SECONDS, TimeUnit.SECONDS);
            }
        }
        return categoryCount;
    }

    private Map<Integer, Integer> convertProfile(Map<Object, Object> cached) {
        Map<Integer, Integer> result = new HashMap<>();
        if (cached == null || cached.isEmpty()) return result;
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
        if (redisTemplate == null) return true;
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private void releaseLock(String lockKey, String lockValue) {
        if (redisTemplate == null) return;
        Object current = redisTemplate.opsForValue().get(lockKey);
        if (current != null && lockValue.equals(String.valueOf(current))) {
            redisTemplate.delete(lockKey);
        }
    }
}
