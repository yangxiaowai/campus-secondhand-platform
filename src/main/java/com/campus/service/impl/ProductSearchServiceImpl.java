package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.search.RankedProduct;
import com.campus.search.SearchMode;
import com.campus.search.SearchPageResult;
import com.campus.search.SearchRecommendCriteria;
import com.campus.service.DegradeService;
import com.campus.service.MetricsService;
import com.campus.service.ProductSearchIndexService;
import com.campus.service.ProductSearchService;
import com.campus.service.ProductService;
import com.campus.service.SearchResultRankService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品搜索：Redis 分布式索引优先，MySQL LIKE 降级；支持约束过滤与综合重排
 */
@Service
@PropertySource("classpath:redis-config.properties")
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchServiceImpl.class);

    private static final int MYSQL_CANDIDATE_LIMIT = 200;

    @Value("${search.shard.count:3}")
    private int shardCount;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSearchIndexService productSearchIndexService;

    @Autowired
    private com.campus.search.embedding.EmbeddingService embeddingService;

    @Autowired
    private DegradeService degradeService;

    @Autowired(required = false)
    private MetricsService metricsService;

    @Autowired
    private SearchResultRankService searchResultRankService;

    @Override
    public SearchPageResult search(String keyword, Integer categoryId, SearchMode mode,
                                   int pageNum, int pageSize) {
        return search(keyword, categoryId, mode, pageNum, pageSize, new SearchRecommendCriteria());
    }

    @Override
    public SearchPageResult search(String keyword, Integer categoryId, SearchMode mode,
                                   int pageNum, int pageSize, SearchRecommendCriteria criteria) {
        int safePageNum = pageNum <= 0 ? 1 : pageNum;
        int safePageSize = pageSize <= 0 ? 12 : pageSize;
        SearchMode safeMode = mode != null ? mode : SearchMode.HYBRID;
        SearchRecommendCriteria safeCriteria = criteria != null ? criteria : new SearchRecommendCriteria();

        long start = System.currentTimeMillis();

        if (!StringUtils.hasText(keyword)) {
            List<Product> candidates = productMapper.findList(null, categoryId, 0);
            return buildRankedPage(candidates, Collections.emptyMap(), null, safeCriteria,
                    safePageNum, safePageSize, safeMode, "mysql", "none", start);
        }

        if (!degradeService.isRedisAvailable()) {
            recordDegradeL2();
            return searchMysqlWithRerank(keyword, categoryId, safeMode, safePageNum, safePageSize,
                    safeCriteria, start, "L2");
        }

        try {
            Map<Integer, Double> scores = productSearchIndexService.search(keyword, categoryId, safeMode);
            if (scores.isEmpty()) {
                recordDegradeL1();
                log.info("[搜索-L1] 索引未命中，回退 MySQL LIKE keyword={}", keyword);
                return searchMysqlWithRerank(keyword, categoryId, safeMode, safePageNum, safePageSize,
                        safeCriteria, start, "L1");
            }

            List<Integer> sortedIds = scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            List<Product> candidates = loadProductsInOrder(sortedIds);
            SearchPageResult ranked = buildRankedPage(candidates, scores, keyword, safeCriteria,
                    safePageNum, safePageSize, safeMode, "redis-index", "none", start);
            recordSearchHit();
            return ranked;
        } catch (Exception e) {
            recordDegradeL1();
            log.warn("[搜索-L1] Redis 检索异常，回退 MySQL: {}", e.getMessage());
            return searchMysqlWithRerank(keyword, categoryId, safeMode, safePageNum, safePageSize,
                    safeCriteria, start, "L1");
        }
    }

    private SearchPageResult searchMysqlWithRerank(String keyword, Integer categoryId, SearchMode mode,
                                                   int pageNum, int pageSize,
                                                   SearchRecommendCriteria criteria,
                                                   long start, String degradeLevel) {
        PageHelper.startPage(1, MYSQL_CANDIDATE_LIMIT);
        List<Product> list = productMapper.findList(keyword, categoryId, 0);
        PageInfo<Product> raw = new PageInfo<>(list);
        Map<Integer, Double> pseudoScores = new HashMap<>();
        int rank = list.size();
        for (Product p : list) {
            if (p != null && p.getId() != null) {
                pseudoScores.put(p.getId(), (double) rank--);
            }
        }
        return buildRankedPage(raw.getList(), pseudoScores, keyword, criteria, pageNum, pageSize,
                mode, "mysql-like", degradeLevel, start);
    }

    private SearchPageResult buildRankedPage(List<Product> candidates,
                                               Map<Integer, Double> relevanceScores,
                                               String keyword,
                                               SearchRecommendCriteria criteria,
                                               int pageNum, int pageSize,
                                               SearchMode mode, String engine, String degrade,
                                               long start) {
        List<RankedProduct> ranked = searchResultRankService.filterAndRank(
                candidates, relevanceScores, keyword, criteria);

        long total = ranked.size();
        int from = (pageNum - 1) * pageSize;
        if (from >= ranked.size()) {
            return new SearchPageResult(Collections.emptyList(), pageNum, pageSize, total,
                    mode, engine, degrade, System.currentTimeMillis() - start, shardCount,
                    relevanceScores,
                    productSearchIndexService.getLastSemanticEngine(),
                    embeddingService.modelName(),
                    Collections.emptyMap());
        }
        int to = Math.min(from + pageSize, ranked.size());
        List<RankedProduct> pageSlice = ranked.subList(from, to);

        List<Product> products = new ArrayList<>();
        Map<Integer, Double> rankScores = new LinkedHashMap<>();
        for (RankedProduct rp : pageSlice) {
            products.add(rp.getProduct());
            rankScores.put(rp.getProduct().getId(), round3(rp.getFinalScore()));
        }

        log.info("[搜索重排] keyword={} 候选{}件 → 过滤排序{}件 → 本页{}件 sortBy={}",
                keyword, candidates.size(), total, products.size(), criteria.getSortBy());

        return new SearchPageResult(products, pageNum, pageSize, total,
                mode, engine, degrade, System.currentTimeMillis() - start, shardCount,
                relevanceScores,
                productSearchIndexService.getLastSemanticEngine(),
                embeddingService.modelName(),
                rankScores);
    }

    private List<Product> loadProductsInOrder(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Product> products = productMapper.findByIds(ids);
        Map<Integer, Product> map = new HashMap<>();
        for (Product p : products) {
            if (p != null && p.getId() != null) {
                map.put(p.getId(), p);
            }
        }
        List<Product> ordered = new ArrayList<>();
        for (Integer id : ids) {
            Product p = map.get(id);
            if (p != null) {
                ordered.add(p);
            }
        }
        return ordered;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private void recordSearchHit() {
        if (metricsService != null) {
            metricsService.recordSearchHit();
        }
    }

    private void recordDegradeL1() {
        if (metricsService != null) {
            metricsService.recordSearchDegradeL1();
        }
    }

    private void recordDegradeL2() {
        if (metricsService != null) {
            metricsService.recordSearchDegradeL2();
        }
    }
}
