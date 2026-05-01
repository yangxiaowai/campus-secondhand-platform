package com.campus.service.impl;

import com.campus.dao.UserMapper;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.recommend.ProductFeature;
import com.campus.recommend.RecommendationItem;
import com.campus.recommend.UserInterestProfile;
import com.campus.service.ProductMatchService;
import com.campus.service.RecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商品匹配引擎实现
 */
@Service
public class ProductMatchServiceImpl implements ProductMatchService {

    private static final Logger logger = LoggerFactory.getLogger(ProductMatchServiceImpl.class);

    private static final int HISTORY_LIMIT = 30;
    private static final int INBOX_MAX_SIZE = 100;
    private static final double MIN_MATCH_SCORE = 0.35D;

    private static final Set<String> STOP_WORDS = new HashSet<>();

    static {
        Collections.addAll(STOP_WORDS, "的", "了", "和", "与", "及", "全新", "二手", "转让", "出售", "求购");
    }

    // 推荐收件箱（userId -> 推荐消息列表，按时间倒序）
    private static final Map<Integer, LinkedList<RecommendationItem>> INBOX_CACHE = new ConcurrentHashMap<>();

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RecommendService recommendService;

    @Override
    public void processPublishedProduct(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }

        ProductFeature feature = extractProductFeature(product);
        logger.info("[分词结果] 商品《{}》关键词={}", product.getName(), feature.getKeywordWeights().keySet());

        List<User> users = userMapper.findAll();
        int pushed = 0;
        for (User user : users) {
            if (!isCandidateUser(user, product.getUserId())) {
                continue;
            }

            UserInterestProfile profile = buildUserProfile(user.getId());
            if (profile.getCategoryWeights().isEmpty() && profile.getKeywordWeights().isEmpty()) {
                continue;
            }

            double score = calculateMatchScore(feature, profile);
            if (score >= MIN_MATCH_SCORE) {
                pushToInbox(user.getId(), product, score);
                pushed++;
                logger.info("[匹配结果] 用户={} 商品ID={} 匹配度={}", user.getId(), product.getId(), formatScore(score));
            }
        }

        logger.info("[事件匹配引擎] 商品ID={} 匹配完成，命中用户数={}", product.getId(), pushed);
    }

    @Override
    public List<RecommendationItem> getInboxRecommendations(Integer userId, Integer limit) {
        if (userId == null || limit == null || limit <= 0) {
            return Collections.emptyList();
        }
        LinkedList<RecommendationItem> items = INBOX_CACHE.get(userId);
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        int size = Math.min(limit, items.size());
        return new ArrayList<>(items.subList(0, size));
    }

    private boolean isCandidateUser(User user, Integer sellerId) {
        if (user == null || user.getId() == null) {
            return false;
        }
        if (user.getRole() != null && user.getRole() == 2) {
            return false;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return false;
        }
        return sellerId == null || !user.getId().equals(sellerId);
    }

    private ProductFeature extractProductFeature(Product product) {
        Map<String, Double> keywords = buildKeywordWeights(tokenize(product.getName(), product.getDescription()));
        return new ProductFeature(product.getId(), product.getCategoryId(), product.getPrice(), keywords);
    }

    private UserInterestProfile buildUserProfile(Integer userId) {
        List<Product> history = recommendService.getBrowseHistory(userId, HISTORY_LIMIT);
        if (history.isEmpty()) {
            return new UserInterestProfile(userId, Collections.emptyMap(), null, Collections.emptyMap());
        }

        Map<Integer, Double> categoryWeights = new HashMap<>();
        Map<String, Double> keywordWeights = new HashMap<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        int priceCount = 0;

        for (Product p : history) {
            if (p.getCategoryId() != null) {
                categoryWeights.merge(p.getCategoryId(), 1.0D, Double::sum);
            }
            for (String token : tokenize(p.getName(), p.getDescription())) {
                keywordWeights.merge(token, 1.0D, Double::sum);
            }
            if (p.getPrice() != null) {
                totalPrice = totalPrice.add(p.getPrice());
                priceCount++;
            }
        }

        BigDecimal avgPrice = priceCount == 0
                ? null
                : totalPrice.divide(BigDecimal.valueOf(priceCount), 2, RoundingMode.HALF_UP);
        return new UserInterestProfile(userId, categoryWeights, avgPrice, keywordWeights);
    }

    private List<String> tokenize(String title, String description) {
        String content = (title == null ? "" : title) + " " + (description == null ? "" : description);
        if (content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] roughTokens = content.toLowerCase()
                .replaceAll("[^\\p{IsHan}a-z0-9]+", " ")
                .trim()
                .split("\\s+");

        List<String> result = new ArrayList<>();
        for (String token : roughTokens) {
            if (token.isEmpty() || STOP_WORDS.contains(token)) {
                continue;
            }
            if (token.length() >= 2) {
                result.add(token);
                // 对中文短语补充双字切分，便于基础关键词匹配
                if (token.matches(".*\\p{IsHan}.*")) {
                    for (int i = 0; i < token.length() - 1; i++) {
                        String bigram = token.substring(i, i + 2);
                        if (!STOP_WORDS.contains(bigram)) {
                            result.add(bigram);
                        }
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Double> buildKeywordWeights(List<String> tokens) {
        if (tokens.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> weights = new HashMap<>();
        for (String token : tokens) {
            weights.merge(token, 1.0D, Double::sum);
        }
        return weights;
    }

    private double calculateMatchScore(ProductFeature feature, UserInterestProfile profile) {
        double categoryScore = calculateCategoryScore(feature, profile);
        double priceScore = calculatePriceScore(feature, profile);
        double keywordScore = calculateKeywordScore(feature, profile);
        return 0.5D * categoryScore + 0.2D * priceScore + 0.3D * keywordScore;
    }

    private double calculateCategoryScore(ProductFeature feature, UserInterestProfile profile) {
        if (feature.getCategoryId() == null || profile.getCategoryWeights().isEmpty()) {
            return 0.0D;
        }
        double target = profile.getCategoryWeights().getOrDefault(feature.getCategoryId(), 0.0D);
        double max = profile.getCategoryWeights().values().stream()
                .max(Comparator.naturalOrder())
                .orElse(1.0D);
        return max == 0.0D ? 0.0D : target / max;
    }

    private double calculatePriceScore(ProductFeature feature, UserInterestProfile profile) {
        if (feature.getPrice() == null || profile.getAvgPrice() == null) {
            return 0.5D;
        }
        double p = feature.getPrice().doubleValue();
        double avg = profile.getAvgPrice().doubleValue();
        if (avg <= 0.0D) {
            return 0.5D;
        }
        double ratio = Math.min(Math.abs(p - avg) / avg, 1.0D);
        return 1.0D - ratio;
    }

    private double calculateKeywordScore(ProductFeature feature, UserInterestProfile profile) {
        if (feature.getKeywordWeights().isEmpty() || profile.getKeywordWeights().isEmpty()) {
            return 0.0D;
        }
        double overlap = 0.0D;
        double total = 0.0D;
        for (Map.Entry<String, Double> entry : feature.getKeywordWeights().entrySet()) {
            total += entry.getValue();
            overlap += Math.min(entry.getValue(), profile.getKeywordWeights().getOrDefault(entry.getKey(), 0.0D));
        }
        return total == 0.0D ? 0.0D : overlap / total;
    }

    private void pushToInbox(Integer userId, Product product, double score) {
        INBOX_CACHE.compute(userId, (k, inbox) -> {
            LinkedList<RecommendationItem> list = (inbox == null) ? new LinkedList<>() : inbox;
            list.addFirst(new RecommendationItem(userId, product, score));
            while (list.size() > INBOX_MAX_SIZE) {
                list.removeLast();
            }
            return list;
        });
    }

    private String formatScore(double score) {
        return String.format("%.2f", score);
    }
}

