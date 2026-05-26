package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.dao.UserMapper;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.service.InboxService;
import com.campus.service.MatchService;
import com.campus.service.NoIndexMatchService;
import com.campus.service.ProductFeatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 无索引推荐服务（暴力匹配）
 * 
 * 退阶版：不使用 Redis 两级索引（IndexService），直接 O(N*M) 遍历
 * 所有用户 × 所有商品，逐个计算匹配度并写入收件箱。
 * 
 * 用于与有索引方案做性能对比测试。
 */
@Service
public class NoIndexMatchServiceImpl implements NoIndexMatchService {

    private static final Logger log = LoggerFactory.getLogger(NoIndexMatchServiceImpl.class);

    private static final double DEFAULT_MIN_SCORE = 0.12;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private MatchService matchService;

    @Autowired
    private ProductFeatureService productFeatureService;

    @Autowired(required = false)
    private InboxService inboxService;

    @Override
    public Map<String, Object> matchAll(double minScore) {
        double threshold = minScore <= 0 ? DEFAULT_MIN_SCORE : minScore;
        long startTime = System.currentTimeMillis();

        List<User> allUsers = userMapper.findAll();
        List<Product> allProducts = productMapper.findList(null, null, null);

        int validUsers = 0;
        int validProducts = allProducts == null ? 0 : allProducts.size();
        long totalComputations = 0;
        int totalPushed = 0;

        Map<Integer, String> userNameById = new LinkedHashMap<>();
        if (allUsers != null) {
            for (User u : allUsers) {
                if (u == null || u.getId() == null) {
                    continue;
                }
                if (u.getStatus() != null && u.getStatus() != 1) {
                    continue;
                }
                validUsers++;
                userNameById.put(u.getId(),
                        u.getNickname() != null ? u.getNickname() : u.getUsername());
            }
        }

        if (validProducts == 0 || userNameById.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("elapsedMs", elapsed);
            result.put("userCount", userNameById.size());
            result.put("productCount", validProducts);
            result.put("totalComputations", 0);
            result.put("totalPushed", 0);
            result.put("threshold", threshold);
            return result;
        }

        log.info("[NoIndex] 开始暴力匹配：{} 用户 × {} 商品，阈值={}",
                userNameById.size(), validProducts, threshold);

        for (Map.Entry<Integer, String> userEntry : userNameById.entrySet()) {
            Integer userId = userEntry.getKey();

            for (Product product : allProducts) {
                if (product == null || product.getId() == null) {
                    continue;
                }

                if (product.getUserId() != null && product.getUserId().equals(userId)) {
                    continue;
                }

                totalComputations++;

                List<String> keywords = productFeatureService.extractKeywords(
                        product.getName() != null ? product.getName() : "");

                Double price = product.getPrice() != null ? product.getPrice().doubleValue() : null;

                double score = matchService.computeMatchScore(
                        userId, product.getCategoryId(), price, keywords);

                if (score >= threshold) {
                    if (inboxService != null) {
                        inboxService.push(userId, product.getId(), score);
                    }
                    totalPushed++;
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        log.info("[NoIndex] 暴力匹配完成：耗时={}ms，计算次数={}，推送次数={}",
                elapsed, totalComputations, totalPushed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("elapsedMs", elapsed);
        result.put("userCount", userNameById.size());
        result.put("productCount", validProducts);
        result.put("totalComputations", totalComputations);
        result.put("totalPushed", totalPushed);
        result.put("threshold", threshold);
        result.put("avgMsPerComputation", totalComputations > 0
                ? String.format("%.3f", (double) elapsed / totalComputations)
                : "0");

        return result;
    }
}