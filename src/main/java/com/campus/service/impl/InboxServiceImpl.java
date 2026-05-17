package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.service.InboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 成员4：推荐收件箱（ZSet，按匹配度降序）
 */
@Service
public class InboxServiceImpl implements InboxService {

    private static final String INBOX_KEY_PREFIX = "user:inbox:";
    private static final long INBOX_TTL_SECONDS = 6 * 60 * 60;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ProductMapper productMapper;

    @Override
    public void push(Integer userId, Integer productId, double matchScore) {
        if (userId == null || productId == null || redisTemplate == null) {
            return;
        }
        String key = inboxKey(userId);
        redisTemplate.opsForZSet().add(key, productId, matchScore);
        redisTemplate.expire(key, INBOX_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public List<Map<String, Object>> listSorted(Integer userId, int limit) {
        int safeLimit = limit <= 0 ? 20 : limit;
        if (userId == null || redisTemplate == null) {
            return Collections.emptyList();
        }
        String key = inboxKey(userId);
        Set<ZSetOperations.TypedTuple<Object>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, safeLimit - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
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
            Map<String, Object> row = new LinkedHashMap<>();
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

    private static String inboxKey(Integer userId) {
        return INBOX_KEY_PREFIX + userId;
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
