package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.service.InboxService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 成员4：推荐收件箱（ZSet 按匹配度排序 + 已读/免打扰/实时通知队列）
 */
@Service
public class InboxServiceImpl implements InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxServiceImpl.class);

    private static final String INBOX_KEY_PREFIX = "user:inbox:";
    private static final String READ_KEY_PREFIX = "user:inbox:read:";
    private static final String DND_KEY_PREFIX = "user:inbox:dnd:";
    private static final String NOTIFY_KEY_PREFIX = "user:inbox:notify:";
    private static final long INBOX_TTL_SECONDS = 6 * 60 * 60;
    private static final int NOTIFY_MAX_LEN = 50;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        if (!isDoNotDisturb(userId)) {
            enqueueNotification(userId, productId, matchScore);
        }
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
        String readKey = readKey(userId);
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
            boolean read = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(readKey, productId));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("productId", productId);
            row.put("matchScore", round3(t.getScore()));
            row.put("read", read);
            row.put("name", p.getName());
            row.put("price", p.getPrice());
            row.put("categoryId", p.getCategoryId());
            row.put("imageUrl", p.getImageUrl());
            row.put("source", "inbox");
            rows.add(row);
        }
        return rows;
    }

    @Override
    public int getUnreadCount(Integer userId) {
        if (userId == null || redisTemplate == null) {
            return 0;
        }
        String key = inboxKey(userId);
        String readKey = readKey(userId);
        Set<Object> members = redisTemplate.opsForZSet().reverseRange(key, 0, -1);
        if (members == null || members.isEmpty()) {
            return 0;
        }
        int unread = 0;
        for (Object member : members) {
            if (!Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(readKey, member))) {
                unread++;
            }
        }
        return unread;
    }

    @Override
    public boolean isDoNotDisturb(Integer userId) {
        if (userId == null || redisTemplate == null) {
            return false;
        }
        Object val = redisTemplate.opsForValue().get(dndKey(userId));
        return "1".equals(String.valueOf(val));
    }

    @Override
    public void setDoNotDisturb(Integer userId, boolean enabled) {
        if (userId == null || redisTemplate == null) {
            return;
        }
        String key = dndKey(userId);
        if (enabled) {
            redisTemplate.opsForValue().set(key, "1", INBOX_TTL_SECONDS, TimeUnit.SECONDS);
        } else {
            redisTemplate.delete(key);
        }
    }

    @Override
    public void markAllRead(Integer userId) {
        if (userId == null || redisTemplate == null) {
            return;
        }
        String inbox = inboxKey(userId);
        String read = readKey(userId);
        Set<Object> members = redisTemplate.opsForZSet().reverseRange(inbox, 0, -1);
        if (members != null) {
            for (Object member : members) {
                redisTemplate.opsForSet().add(read, member);
            }
            redisTemplate.expire(read, INBOX_TTL_SECONDS, TimeUnit.SECONDS);
        }
        redisTemplate.delete(notifyKey(userId));
    }

    @Override
    public void markRead(Integer userId, Integer productId) {
        if (userId == null || productId == null || redisTemplate == null) {
            return;
        }
        String read = readKey(userId);
        redisTemplate.opsForSet().add(read, productId);
        redisTemplate.expire(read, INBOX_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public boolean isRead(Integer userId, Integer productId) {
        if (userId == null || productId == null || redisTemplate == null) {
            return true;
        }
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(readKey(userId), productId));
    }

    /**
     * 取出并清空待推送提醒（拉取即确认，避免 since 时间戳漏推）
     */
    @Override
    public List<Map<String, Object>> pollNotifications(Integer userId, long since) {
        if (userId == null || redisTemplate == null) {
            return Collections.emptyList();
        }
        if (isDoNotDisturb(userId)) {
            return Collections.emptyList();
        }
        String key = notifyKey(userId);
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        redisTemplate.delete(key);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = raw.size() - 1; i >= 0; i--) {
            Map<String, Object> item = parseNotification(raw.get(i));
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private void enqueueNotification(Integer userId, Integer productId, double matchScore) {
        try {
            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("productId", productId);
            evt.put("matchScore", round3(matchScore));
            evt.put("pushedAt", System.currentTimeMillis());
            Product p = productMapper.findById(productId);
            if (p != null) {
                evt.put("name", p.getName());
                evt.put("price", p.getPrice());
                evt.put("imageUrl", p.getImageUrl());
            }
            String json = objectMapper.writeValueAsString(evt);
            String notifyKey = notifyKey(userId);
            redisTemplate.opsForList().leftPush(notifyKey, json);
            redisTemplate.opsForList().trim(notifyKey, 0, NOTIFY_MAX_LEN - 1);
            redisTemplate.expire(notifyKey, INBOX_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[收件箱] 写入通知队列失败 userId={}, productId={}: {}", userId, productId, e.getMessage());
        }
    }

    private Map<String, Object> parseNotification(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof String) {
                return objectMapper.readValue((String) raw, new TypeReference<Map<String, Object>>() {});
            }
            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) raw;
                return map;
            }
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("[收件箱] 解析通知失败: {}", e.getMessage());
            return null;
        }
    }

    private static String inboxKey(Integer userId) {
        return INBOX_KEY_PREFIX + userId;
    }

    private static String readKey(Integer userId) {
        return READ_KEY_PREFIX + userId;
    }

    private static String dndKey(Integer userId) {
        return DND_KEY_PREFIX + userId;
    }

    private static String notifyKey(Integer userId) {
        return NOTIFY_KEY_PREFIX + userId;
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
