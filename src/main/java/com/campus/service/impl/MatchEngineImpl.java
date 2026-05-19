package com.campus.service.impl;

import com.campus.dao.UserMapper;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.service.IndexService;
import com.campus.service.InboxService;
import com.campus.service.MatchEngine;
import com.campus.service.MatchService;
import com.campus.service.MetricsService;
import com.campus.service.ProductFeatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 成员B：发布事件驱动的匹配与收件箱写入
 */
@Service
public class MatchEngineImpl implements MatchEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchEngineImpl.class);

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

    @Autowired
    private IndexService indexService;

    @Autowired
    private InboxService inboxService;

    @Autowired
    private MetricsService metricsService;

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

        int totalUsers = userMapper.count();
        if (totalUsers <= 0) {
            List<User> all = userMapper.findAll();
            totalUsers = all == null ? 0 : all.size();
        }

        Set<Integer> candidateIds = indexService.findCandidateUserIds(
                product.getCategoryId(), keywords, totalUsers);

        Map<Integer, User> userById = new HashMap<>();
        List<User> users = userMapper.findAll();
        if (users != null) {
            for (User u : users) {
                if (u != null && u.getId() != null) {
                    userById.put(u.getId(), u);
                }
            }
        }
        if (userById.isEmpty()) {
            return;
        }

        Iterable<Integer> targetUserIds;
        if (candidateIds == null || candidateIds.isEmpty()) {
            targetUserIds = userById.keySet();
            log.info("[成员4-索引] 索引未命中，回退全量遍历 {} 人", userById.size());
        } else {
            targetUserIds = candidateIds;
        }

        long matchStart = System.currentTimeMillis();
        Integer sellerId = product.getUserId();
        int pushed = 0;
        Double productPrice = product.getPrice() != null ? product.getPrice().doubleValue() : null;
        for (Integer uid : targetUserIds) {
            User u = userById.get(uid);
            if (u == null) {
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
                    productPrice,
                    keywords);
            if (score < INBOX_MIN_SCORE) {
                continue;
            }
            inboxService.push(u.getId(), product.getId(), score);
            pushed++;
            log.debug("[成员4-收件箱] userId={}, productId={}, matchScore={}", u.getId(), product.getId(), score);
        }
        metricsService.recordMatch(System.currentTimeMillis() - matchStart, pushed);
        log.info("[成员B-MatchEngine] 发布匹配完成 productId={}，索引候选={}，写入收件箱用户数={}",
                product.getId(),
                candidateIds == null ? "全量" : candidateIds.size(),
                pushed);
    }
}
