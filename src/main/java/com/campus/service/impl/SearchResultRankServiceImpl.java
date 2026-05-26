package com.campus.service.impl;

import com.campus.entity.Product;
import com.campus.entity.UserProfile;
import com.campus.search.RankedProduct;
import com.campus.search.SearchRecommendCriteria;
import com.campus.service.MatchService;
import com.campus.service.ProductFeatureService;
import com.campus.service.SearchResultRankService;
import com.campus.service.UserProfileService;
import com.campus.util.PricePreferenceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 搜索列表精细化排序
 * <p>
 * finalScore = w1×相关度 + w2×画像匹配 + w3×时效性 + w4×价格契合（默认 0.40/0.25/0.20/0.15）
 */
@Service
@PropertySource("classpath:redis-config.properties")
public class SearchResultRankServiceImpl implements SearchResultRankService {

    private static final Logger log = LoggerFactory.getLogger(SearchResultRankServiceImpl.class);

    @Value("${search.rank.weight.relevance:0.40}")
    private double wRelevance;

    @Value("${search.rank.weight.match:0.25}")
    private double wMatch;

    @Value("${search.rank.weight.freshness:0.20}")
    private double wFreshness;

    @Value("${search.rank.weight.price:0.15}")
    private double wPrice;

    @Value("${search.rank.freshness.halfLifeDays:30}")
    private int freshnessHalfLifeDays;

    @Autowired
    private MatchService matchService;

    @Autowired
    private ProductFeatureService productFeatureService;

    @Autowired
    private UserProfileService userProfileService;

    @Override
    public List<RankedProduct> filterAndRank(List<Product> products,
                                             Map<Integer, Double> relevanceScores,
                                             String searchKeyword,
                                             SearchRecommendCriteria criteria) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        SearchRecommendCriteria safe = criteria != null ? criteria : new SearchRecommendCriteria();
        Map<Integer, Double> rel = relevanceScores != null ? relevanceScores : Collections.emptyMap();

        double relMax = rel.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (relMax <= 0) {
            relMax = 1.0;
        }

        // 价格/时效仅参与排序加权，不隐藏商品；画像区间用于 priceFit，不用于过滤
        Double pricePrefMin = safe.getMinPrice();
        Double pricePrefMax = safe.getMaxPrice();
        if (safe.getUserId() != null && safe.isUseProfilePriceRange()
                && pricePrefMin == null && pricePrefMax == null) {
            UserProfile.PriceRange range = userProfileService.getPriceRange(safe.getUserId());
            if (range != null) {
                if (range.getMinPrice() != null && range.getMinPrice() < Double.MAX_VALUE / 4) {
                    pricePrefMin = range.getMinPrice();
                }
                if (range.getMaxPrice() != null && range.getMaxPrice() > 0
                        && range.getMaxPrice() < Double.MAX_VALUE / 4) {
                    pricePrefMax = range.getMaxPrice();
                }
            }
        }

        long now = System.currentTimeMillis();
        List<RankedProduct> ranked = new ArrayList<>();

        for (Product p : products) {
            if (p == null || p.getId() == null) {
                continue;
            }
            if (p.getStatus() != null && p.getStatus() != 0) {
                continue;
            }

            double relevanceNorm = rel.getOrDefault(p.getId(), 0.5) / relMax;
            relevanceNorm = clamp01(relevanceNorm);

            double matchScore = 0.0;
            if (safe.getUserId() != null) {
                String title = p.getName() != null ? p.getName() : "";
                List<String> kw = productFeatureService.extractKeywords(title);
                matchScore = matchService.computeMatchScore(
                        safe.getUserId(),
                        p.getCategoryId(),
                        p.getPrice() != null ? p.getPrice().doubleValue() : null,
                        kw);
            }

            double freshness = freshnessScore(p.getCreateTime(), now, safe.getMaxPublishDays());
            double priceFit = computePriceFitScore(
                    safe, p.getPrice() != null ? p.getPrice().doubleValue() : null,
                    pricePrefMin, pricePrefMax);

            double finalScore = wRelevance * relevanceNorm
                    + wMatch * matchScore
                    + wFreshness * freshness
                    + wPrice * priceFit;

            ranked.add(new RankedProduct(p, finalScore, relevanceNorm, matchScore, freshness, priceFit));
        }

        Comparator<RankedProduct> comparator = buildComparator(safe.getSortBy());
        ranked.sort(comparator);

        log.debug("[搜索重排] 候选{}件，排序后{}件，sortBy={}",
                products.size(), ranked.size(), safe.getSortBy());
        return ranked;
    }

    /**
     * 时效分：基础指数衰减；若用户选了「近 N 天」则优先提升该窗口内商品，但不剔除其它商品。
     */
    private double freshnessScore(Date createTime, long nowMs, Integer preferWithinDays) {
        double base = freshnessScoreBase(createTime, nowMs);
        if (preferWithinDays == null || preferWithinDays <= 0 || createTime == null) {
            return base;
        }
        long ageMs = nowMs - createTime.getTime();
        if (ageMs <= (long) preferWithinDays * 86400000L) {
            return Math.min(1.0, base * 1.15 + 0.1);
        }
        return base * 0.85;
    }

    /** 指数衰减：越新越高，半衰期由配置决定 */
    private double freshnessScoreBase(Date createTime, long nowMs) {
        if (createTime == null) {
            return 0.5;
        }
        double days = Math.max(0, (nowMs - createTime.getTime()) / (86400000.0));
        double half = Math.max(1, freshnessHalfLifeDays);
        return Math.exp(-0.693147 * days / half);
    }

    private double computePriceFitScore(SearchRecommendCriteria safe, Double price,
                                        Double prefMin, Double prefMax) {
        if (price == null) {
            return 0.5;
        }
        boolean userSetRange = safe.getMinPrice() != null || safe.getMaxPrice() != null;
        if (userSetRange) {
            return priceFitScore(price, prefMin, prefMax);
        }
        if (safe.getUserId() != null && safe.isUseProfilePriceRange()) {
            UserProfile.PriceRange range = userProfileService.getPriceRange(safe.getUserId());
            return PricePreferenceHelper.preferenceScore(range, price);
        }
        return priceFitScore(price, prefMin, prefMax);
    }

    private static double priceFitScore(Double price, Double min, Double max) {
        if (price == null) {
            return 0.5;
        }
        if (min == null && max == null) {
            return 0.6;
        }
        double lo = min != null ? min : 0;
        double hi = max != null ? max : price;
        if (min != null && max != null && lo > hi) {
            return 0.5;
        }
        if (price >= lo && price <= hi) {
            return 1.0;
        }
        double span = Math.max(hi - lo, 1.0);
        double dist = price < lo ? lo - price : price - hi;
        return 1.0 / (1.0 + dist / span);
    }

    private static Comparator<RankedProduct> buildComparator(SearchRecommendCriteria.SortBy sortBy) {
        if (sortBy == null) {
            sortBy = SearchRecommendCriteria.SortBy.BEST_FIT;
        }
        switch (sortBy) {
            case NEWEST:
                return Comparator
                        .comparing(RankedProduct::getProduct,
                                Comparator.comparing(Product::getCreateTime,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                        .thenComparing(Comparator.comparingDouble(RankedProduct::getFinalScore).reversed());
            case PRICE_ASC:
                return Comparator
                        .comparing(RankedProduct::getProduct,
                                Comparator.comparing(Product::getPrice,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .thenComparing(Comparator.comparingDouble(RankedProduct::getFinalScore).reversed());
            case PRICE_DESC:
                return Comparator
                        .comparing(RankedProduct::getProduct,
                                Comparator.comparing(Product::getPrice,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                        .thenComparing(Comparator.comparingDouble(RankedProduct::getFinalScore).reversed());
            case BEST_FIT:
            default:
                return Comparator.comparingDouble(RankedProduct::getFinalScore).reversed()
                        .thenComparing(RankedProduct::getProduct,
                                Comparator.comparing(Product::getCreateTime,
                                        Comparator.nullsLast(Comparator.reverseOrder())));
        }
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }
}
