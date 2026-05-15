package com.campus.service.impl;

import com.campus.dao.UserMapper;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.service.MatchEngine;
import com.campus.service.MatchService;
import com.campus.service.ProductFeatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 成员B：发布事件驱动的匹配与收件箱写入
 */
@Service
public class MatchEngineImpl implements MatchEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchEngineImpl.class);

    /** Redis ZSet：score=匹配度，member=商品ID字符串 */
    private static final String INBOX_KEY_PREFIX = "user:inbox:";
    private static final long INBOX_TTL_SECONDS = 30L * 24 * 60 * 60;
    /** 低于阈值不写入收件箱，减少噪声 */
    private static final double INBOX_MIN_SCORE = 0.12;

    @Autowired
    private ProductFeatureService productFeatureService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onProductPublished(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        if (redisTemplate == null) {
            log.warn("[成员B-MatchEngine] RedisTemplate 不可用，跳过后续匹配与收件箱");
            return;
        }

        String title = product.getName() != null ? product.getName() : "";
        List<String> tokens = productFeatureService.tokenizeTitle(title);
        List<String> keywords = productFeatureService.extractKeywords(title);

        // 验收：控制台打印分词结果
        System.out.println("[成员B-分词] productId=" + product.getId()
                + ", title=" + title
                + ", tokens=" + tokens
                + ", keywords=" + keywords);
        log.info("[成员B-分词] productId={}, title={}, tokens={}, keywords={}",
                product.getId(), title, tokens, keywords);

        productFeatureService.saveProductFeatures(product, keywords);

        List<User> users = userMapper.findAll();
        if (users == null || users.isEmpty()) {
            return;
        }

        Integer sellerId = product.getUserId();
        int pushed = 0;
        for (User u : users) {
            if (u == null || u.getId() == null) {
                continue;
            }
            if (u.getStatus() != null && u.getStatus() != 1) {
                continue;
            }
            if (sellerId != null && sellerId.equals(u.getId())) {
                continue;
            }
            double score = matchService.computeMatchScore(
                    u.getId(),
                    product.getCategoryId(),
                    product.getPrice() != null ? product.getPrice().doubleValue() : null,
                    keywords);
            if (score < INBOX_MIN_SCORE) {
                continue;
            }
            String inboxKey = INBOX_KEY_PREFIX + u.getId();
            redisTemplate.opsForZSet().add(inboxKey, product.getId(), score);
            redisTemplate.expire(inboxKey, INBOX_TTL_SECONDS, TimeUnit.SECONDS);
            pushed++;
            log.debug("[成员B-收件箱] userId={}, productId={}, matchScore={}", u.getId(), product.getId(), score);
        }
        log.info("[成员B-MatchEngine] 发布匹配完成 productId={}，写入收件箱用户数={}", product.getId(), pushed);
    }
}
