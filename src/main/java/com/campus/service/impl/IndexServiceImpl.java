package com.campus.service.impl;

import com.campus.dao.UserMapper;
import com.campus.dao.UserProfileMapper;
import com.campus.service.IndexService;
import com.campus.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 成员4：两级索引实现
 * <p>
 * Redis Set：
 * idx:category:{categoryId}:users
 * idx:keyword:{keyword}:users
 */
@Service
public class IndexServiceImpl implements IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexServiceImpl.class);

    private static final String CATEGORY_INDEX_PREFIX = "idx:category:";
    private static final String KEYWORD_INDEX_PREFIX = "idx:keyword:";
    private static final String INDEX_SUFFIX = ":users";

    private static final long INDEX_TTL_SECONDS = 24 * 60 * 60;
    /** 归一化分类权重超过该阈值则加入分类索引 */
    private static final double CATEGORY_WEIGHT_THRESHOLD = 0.1;
    /** 关键词浏览频次超过该阈值则加入关键词索引 */
    private static final int KEYWORD_COUNT_THRESHOLD = 2;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public void rebuildUserIndex(Integer userId) {
        if (userId == null || redisTemplate == null) {
            return;
        }
        try {
            Map<Integer, Double> normalized = userProfileService.getNormalizedCategoryWeights(userId);
            if (normalized != null) {
                for (Map.Entry<Integer, Double> e : normalized.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null
                            && e.getValue() > CATEGORY_WEIGHT_THRESHOLD) {
                        addToSet(categoryKey(e.getKey()), userId);
                    }
                }
            }

            Map<String, Integer> keywords = userProfileService.getKeywords(userId);
            if (keywords != null) {
                for (Map.Entry<String, Integer> e : keywords.entrySet()) {
                    if (StringUtils.hasText(e.getKey()) && e.getValue() != null
                            && e.getValue() > KEYWORD_COUNT_THRESHOLD) {
                        addToSet(keywordKey(e.getKey()), userId);
                    }
                }
            }
            log.debug("[成员4-索引] 用户索引已更新 userId={}", userId);
        } catch (Exception e) {
            log.warn("[成员4-索引] 更新用户索引失败 userId={}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void rebuildAllIndexes() {
        if (redisTemplate == null) {
            log.warn("[成员4-索引] Redis 不可用，跳过全量索引重建");
            return;
        }
        Set<Integer> userIds = new LinkedHashSet<>();
        try {
            List<Integer> fromProfile = userProfileMapper.findAllUserIds();
            if (fromProfile != null) {
                userIds.addAll(fromProfile);
            }
        } catch (Exception e) {
            log.warn("[成员4-索引] 读取画像用户列表失败: {}", e.getMessage());
        }
        try {
            if (userMapper.findAll() != null) {
                userMapper.findAll().stream()
                        .filter(u -> u != null && u.getId() != null)
                        .forEach(u -> userIds.add(u.getId()));
            }
        } catch (Exception e) {
            log.warn("[成员4-索引] 读取用户列表失败: {}", e.getMessage());
        }
        int count = 0;
        for (Integer userId : userIds) {
            rebuildUserIndex(userId);
            count++;
        }
        log.info("[成员4-索引] 全量索引重建完成，处理用户数={}", count);
    }

    @Override
    public Set<Integer> findCandidateUserIds(Integer categoryId, List<String> keywords, int totalUserCount) {
        if (redisTemplate == null) {
            return null;
        }
        Set<Integer> categoryUsers = loadCategoryUsers(categoryId);
        Set<Integer> keywordUsers = loadKeywordUnion(keywords);

        Set<Integer> result;
        if (!categoryUsers.isEmpty() && !keywordUsers.isEmpty()) {
            result = new HashSet<>(categoryUsers);
            result.retainAll(keywordUsers);
        } else if (!categoryUsers.isEmpty()) {
            result = categoryUsers;
        } else if (!keywordUsers.isEmpty()) {
            result = keywordUsers;
        } else {
            log.info("[成员4-索引] 索引未命中，将回退全量遍历（总用户约 {} 人）", totalUserCount);
            return null;
        }

        log.info("[成员4-索引] 索引命中{}人，无需遍历{}人", result.size(), totalUserCount);
        return result;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledRebuildAllIndexes() {
        log.info("[成员4-索引] 定时任务：开始全量重建两级索引");
        rebuildAllIndexes();
    }

    private Set<Integer> loadCategoryUsers(Integer categoryId) {
        if (categoryId == null) {
            return Collections.emptySet();
        }
        return loadUserSet(categoryKey(categoryId));
    }

    private Set<Integer> loadKeywordUnion(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> union = new HashSet<>();
        for (String kw : keywords) {
            if (!StringUtils.hasText(kw)) {
                continue;
            }
            union.addAll(loadUserSet(keywordKey(kw.trim())));
        }
        return union;
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> loadUserSet(String key) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(key);
            if (members == null || members.isEmpty()) {
                return Collections.emptySet();
            }
            Set<Integer> ids = new HashSet<>();
            for (Object m : members) {
                Integer id = parseUserId(m);
                if (id != null) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("[成员4-索引] 读取索引失败 key={}: {}", key, e.getMessage());
            return Collections.emptySet();
        }
    }

    private void addToSet(String key, Integer userId) {
        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key, INDEX_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private static String categoryKey(Integer categoryId) {
        return CATEGORY_INDEX_PREFIX + categoryId + INDEX_SUFFIX;
    }

    private static String keywordKey(String keyword) {
        return KEYWORD_INDEX_PREFIX + keyword + INDEX_SUFFIX;
    }

    private static Integer parseUserId(Object raw) {
        if (raw instanceof Integer) {
            return (Integer) raw;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }
}
