package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.search.SearchMode;
import com.campus.search.SearchPageResult;
import com.campus.service.DegradeService;
import com.campus.service.MetricsService;
import com.campus.service.ProductSearchIndexService;
import com.campus.service.ProductSearchService;
import com.campus.service.ProductService;
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
 * 商品搜索：Redis 分布式索引优先，MySQL LIKE 降级
 */
@Service
@PropertySource("classpath:redis-config.properties")
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchServiceImpl.class);

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

    @Override
    public SearchPageResult search(String keyword, Integer categoryId, SearchMode mode,
                                   int pageNum, int pageSize) {
        int safePageNum = pageNum <= 0 ? 1 : pageNum;
        int safePageSize = pageSize <= 0 ? 12 : pageSize;
        SearchMode safeMode = mode != null ? mode : SearchMode.HYBRID;

        if (!StringUtils.hasText(keyword)) {
            PageInfo<Product> page = productService.findList(null, categoryId, safePageNum, safePageSize);
            return SearchPageResult.fromPageInfo(page, safeMode, "mysql", "none");
        }

        long start = System.currentTimeMillis();
        if (!degradeService.isRedisAvailable()) {
            recordDegradeL2();
            PageInfo<Product> page = productService.findList(keyword, categoryId, safePageNum, safePageSize);
            return SearchPageResult.fromPageInfo(page, safeMode, "mysql-like", "L2");
        }

        try {
            Map<Integer, Double> scores = productSearchIndexService.search(keyword, categoryId, safeMode);
            if (scores.isEmpty()) {
                recordDegradeL1();
                log.info("[搜索-L1] 索引未命中，回退 MySQL LIKE keyword={}", keyword);
                PageInfo<Product> page = productService.findList(keyword, categoryId, safePageNum, safePageSize);
                SearchPageResult r = SearchPageResult.fromPageInfo(page, safeMode, "mysql-like", "L1");
                return withTook(r, start, shardCount);
            }

            List<Integer> sortedIds = scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            long total = sortedIds.size();
            int from = (safePageNum - 1) * safePageSize;
            if (from >= sortedIds.size()) {
                return new SearchPageResult(Collections.emptyList(), safePageNum, safePageSize, total,
                        safeMode, "redis-index", "none", System.currentTimeMillis() - start, shardCount, scores,
                        productSearchIndexService.getLastSemanticEngine(), embeddingService.modelName());
            }
            int to = Math.min(from + safePageSize, sortedIds.size());
            List<Integer> pageIds = sortedIds.subList(from, to);
            List<Product> products = productMapper.findByIds(pageIds);
            Map<Integer, Product> productMap = new HashMap<>();
            for (Product p : products) {
                if (p != null && p.getId() != null) {
                    productMap.put(p.getId(), p);
                }
            }
            List<Product> ordered = new ArrayList<>();
            for (Integer id : pageIds) {
                Product p = productMap.get(id);
                if (p != null) {
                    ordered.add(p);
                }
            }
            recordSearchHit();
            return new SearchPageResult(ordered, safePageNum, safePageSize, total,
                    safeMode, "redis-index", "none", System.currentTimeMillis() - start, shardCount, scores,
                    productSearchIndexService.getLastSemanticEngine(), embeddingService.modelName());
        } catch (Exception e) {
            recordDegradeL1();
            log.warn("[搜索-L1] Redis 检索异常，回退 MySQL: {}", e.getMessage());
            PageInfo<Product> page = productService.findList(keyword, categoryId, safePageNum, safePageSize);
            return withTook(SearchPageResult.fromPageInfo(page, safeMode, "mysql-like", "L1"), start, shardCount);
        }
    }

    private SearchPageResult withTook(SearchPageResult r, long start, int shards) {
        return new SearchPageResult(r.getList(), r.getPageNum(), r.getPageSize(), r.getTotal(),
                r.getSearchMode(), r.getEngine(), r.getDegradeLevel(),
                System.currentTimeMillis() - start, shards, r.getScores(),
                productSearchIndexService.getLastSemanticEngine(), embeddingService.modelName());
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
