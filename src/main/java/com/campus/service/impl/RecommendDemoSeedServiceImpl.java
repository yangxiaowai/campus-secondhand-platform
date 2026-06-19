package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.dao.UserMapper;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.service.IndexService;
import com.campus.service.MatchEngine;
import com.campus.service.ProductFeatureService;
import com.campus.service.RecommendDemoSeedService;
import com.campus.service.RecommendService;
import com.campus.service.UserProfileService;
import com.campus.util.MD5Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成员4 验收：买家养画像 + 卖家发布《算法导论》→ 收件箱写入
 */
@Service
public class RecommendDemoSeedServiceImpl implements RecommendDemoSeedService {

    private static final Logger log = LoggerFactory.getLogger(RecommendDemoSeedServiceImpl.class);

    private static final String BUYER_USERNAME = "buyer_cs";
    private static final String SELLER_USERNAME = "seller_li";
    private static final String DEMO_PASSWORD = "123456";
    private static final String DEMO_PRODUCT_NAME = "算法导论第4版";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private ProductFeatureService productFeatureService;

    @Autowired(required = false)
    private MatchEngine matchEngine;

    @Autowired(required = false)
    private IndexService indexService;

    @Override
    public Map<String, Object> seedRecommendDemo() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            User buyer = ensureUser(BUYER_USERNAME, "演示买家", 1);
            User seller = ensureUser(SELLER_USERNAME, "演示卖家", 1);

            int categoryBooks = 1;
            List<String> browseKeywords = productFeatureService.extractKeywords("算法导论 计算机教材");
            for (int i = 0; i < 4; i++) {
                userProfileService.recordBrowse(buyer.getId(), categoryBooks, 45.0, browseKeywords);
            }
            if (indexService != null) {
                indexService.rebuildUserIndex(buyer.getId());
            }

            Product product = findOrCreateDemoProduct(seller.getId(), categoryBooks);
            if (matchEngine != null) {
                matchEngine.onProductPublished(product);
            } else {
                result.put("warning", "MatchEngine 未注入，未写入收件箱");
            }

            Map<String, Object> accounts = new LinkedHashMap<>();
            Map<String, Object> buyerInfo = new LinkedHashMap<>();
            buyerInfo.put("userId", buyer.getId());
            buyerInfo.put("username", buyer.getUsername());
            buyerInfo.put("password", DEMO_PASSWORD);
            accounts.put("buyer", buyerInfo);

            Map<String, Object> sellerInfo = new LinkedHashMap<>();
            sellerInfo.put("userId", seller.getId());
            sellerInfo.put("username", seller.getUsername());
            sellerInfo.put("password", DEMO_PASSWORD);
            accounts.put("seller", sellerInfo);

            Map<String, String> verifyUrls = new LinkedHashMap<>();
            verifyUrls.put("inbox", "/user/inbox");
            verifyUrls.put("indexStats", "/test/index/stats?categoryId=1&keyword=算法&userId=" + buyer.getId());
            verifyUrls.put("profile", "/user/profile");

            result.put("success", true);
            result.put("message", "演示数据已注入：买家画像与索引已更新，已发布/触发匹配商品");
            result.put("accounts", accounts);
            result.put("productId", product.getId());
            result.put("productName", product.getName());
            result.put("verifyUrls", verifyUrls);
            log.info("[演示种子] buyerId={}, sellerId={}, productId={}",
                    buyer.getId(), seller.getId(), product.getId());
        } catch (Exception e) {
            log.error("[演示种子] 失败", e);
            result.put("success", false);
            result.put("message", "注入失败: " + e.getMessage());
        }
        return result;
    }

    private User ensureUser(String username, String nickname, int role) {
        User existing = userMapper.findByUsername(username);
        if (existing != null) {
            return existing;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(MD5Util.md5(DEMO_PASSWORD));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        return userMapper.findByUsername(username);
    }

    private Product findOrCreateDemoProduct(Integer sellerId, int categoryId) {
        List<Product> list = productMapper.findList("算法导论", categoryId, 0);
        if (list != null) {
            for (Product p : list) {
                if (p != null && DEMO_PRODUCT_NAME.equals(p.getName()) && sellerId.equals(p.getUserId())) {
                    return productMapper.findById(p.getId());
                }
            }
        }
        Product product = new Product();
        product.setName(DEMO_PRODUCT_NAME);
        product.setPrice(new BigDecimal("88.00"));
        product.setDescription("计算机经典教材，九成新，适合考研与专业课复习。");
        product.setImageUrl("/static/img/placeholder.svg");
        product.setCategoryId(categoryId);
        product.setUserId(sellerId);
        product.setStatus(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return productMapper.findById(product.getId());
    }
}
