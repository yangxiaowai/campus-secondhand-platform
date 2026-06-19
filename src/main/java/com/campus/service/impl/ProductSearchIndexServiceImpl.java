package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.search.SearchMode;
import com.campus.search.SearchShardRouter;
import com.campus.search.SemanticSynonymExpander;
import com.campus.search.TfIdfVectorUtil;
import com.campus.search.embedding.EmbeddingService;
import com.campus.search.embedding.EmbeddingVectorCodec;
import com.campus.search.embedding.ProductVectorDocument;
import com.campus.search.redis.RedisStackVectorIndexService;
import com.campus.service.ProductFeatureService;
import com.campus.service.ProductSearchIndexService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 分布式商品搜索索引：分片倒排 + 真实 Embedding（Redis Stack KNN / search:vec 稠密余弦）
 */
@Service
@PropertySource("classpath:redis-config.properties")
public class ProductSearchIndexServiceImpl implements ProductSearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchIndexServiceImpl.class);

    @Value("${search.shard.count:3}")
    private int shardCount;

    @Value("${search.index.ttl.seconds:604800}")
    private long indexTtlSeconds;

    @Value("${search.keyword.weight:0.6}")
    private double keywordWeight;

    @Value("${search.semantic.weight:0.4}")
    private double semanticWeight;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductFeatureService productFeatureService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private RedisStackVectorIndexService redisStackVectorIndexService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String lastSemanticEngine = "tfidf";

    private final ExecutorService searchExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));

    @Override
    public void indexProduct(Product product) {
        if (product == null || product.getId() == null || redisTemplate == null) {
            return;
        }
        if (product.getStatus() != null && product.getStatus() != 0) {
            removeProduct(product.getId());
            return;
        }
        try {
            removeProduct(product.getId());
            List<String> terms = buildIndexTerms(product);
            if (terms.isEmpty()) {
                return;
            }
            int shardId = SearchShardRouter.shardForProductId(product.getId(), shardCount);
            Map<String, Integer> termFreq = new HashMap<>();
            for (String term : terms) {
                termFreq.merge(term, 1, Integer::sum);
                String key = SearchShardRouter.productTermKey(shardId, term);
                redisTemplate.opsForSet().add(key, product.getId());
                redisTemplate.expire(key, indexTtlSeconds, TimeUnit.SECONDS);
                redisTemplate.opsForValue().increment(SearchShardRouter.docFreqKey(term));
            }
            int totalDocs = registerIndexedDoc(product.getId());
            Map<String, Integer> docFreq = loadDocFreq(terms);
            Map<String, Double> tfidf = TfIdfVectorUtil.buildTfIdf(termFreq, docFreq, totalDocs);
            String indexText = buildIndexText(product);
            float[] embedding = embeddingService.embed(indexText);
            ProductVectorDocument vecDoc = embedding != null
                    ? ProductVectorDocument.dense(embeddingService.modelName(),
                    embedding.length, embedding, tfidf)
                    : ProductVectorDocument.tfidfOnly(tfidf);
            saveVectorDocument(product.getId(), vecDoc);
            if (embedding != null) {
                redisStackVectorIndexService.upsertProduct(product, embedding);
            }

            Map<String, Object> docMeta = new HashMap<>();
            docMeta.put("categoryId", product.getCategoryId());
            docMeta.put("shardId", shardId);
            docMeta.put("terms", terms);
            redisTemplate.opsForHash().putAll(SearchShardRouter.productDocKey(product.getId()), docMeta);
            redisTemplate.expire(SearchShardRouter.productDocKey(product.getId()),
                    indexTtlSeconds, TimeUnit.SECONDS);
            log.debug("[搜索索引] productId={}, shard={}, terms={}", product.getId(), shardId, terms.size());
        } catch (Exception e) {
            log.warn("[搜索索引] 建立失败 productId={}: {}", product.getId(), e.getMessage());
        }
    }

    @Override
    public void removeProduct(Integer productId) {
        if (productId == null || redisTemplate == null) {
            return;
        }
        try {
            Map<Object, Object> doc = redisTemplate.opsForHash()
                    .entries(SearchShardRouter.productDocKey(productId));
            if (doc != null && doc.get("terms") != null) {
                int shardId = parseInt(doc.get("shardId"), SearchShardRouter.shardForProductId(productId, shardCount));
                List<String> terms = parseTerms(doc.get("terms"));
                for (String term : terms) {
                    redisTemplate.opsForSet().remove(SearchShardRouter.productTermKey(shardId, term), productId);
                }
            }
            redisTemplate.delete(SearchShardRouter.productVectorKey(productId));
            redisTemplate.delete(SearchShardRouter.productDocKey(productId));
            redisStackVectorIndexService.deleteProduct(productId);
            redisTemplate.opsForSet().remove("search:docs", productId);
            refreshDocCount();
        } catch (Exception e) {
            log.warn("[搜索索引] 删除失败 productId={}: {}", productId, e.getMessage());
        }
    }

    @Override
    public void rebuildAllIndexes() {
        if (redisTemplate == null) {
            log.warn("[搜索索引] Redis 不可用，跳过全量重建");
            return;
        }
        clearSearchKeys();
        redisStackVectorIndexService.dropIndex();
        List<Product> products = productMapper.findList(null, null, 0);
        if (products == null) {
            return;
        }
        int count = 0;
        for (Product p : products) {
            if (p != null && p.getId() != null) {
                indexProduct(p);
                count++;
            }
        }
        log.info("[搜索索引] 全量重建完成，商品数={}", count);
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void scheduledRebuild() {
        log.info("[搜索索引] 定时全量重建");
        rebuildAllIndexes();
    }

    @Override
    public Map<Integer, Double> search(String keyword, Integer categoryId, SearchMode mode) {
        if (!StringUtils.hasText(keyword) || redisTemplate == null) {
            return Collections.emptyMap();
        }
        SearchMode safeMode = mode != null ? mode : SearchMode.HYBRID;
        List<String> queryTerms = productFeatureService.extractKeywords(keyword.trim());
        if (queryTerms.isEmpty()) {
            queryTerms = productFeatureService.tokenizeTitle(keyword.trim());
        }
        if (queryTerms.isEmpty()) {
            return Collections.emptyMap();
        }

        long start = System.currentTimeMillis();

        Map<Integer, Double> keywordScores = safeMode == SearchMode.SEMANTIC
                ? Collections.emptyMap()
                : scatterGatherKeyword(queryTerms);
        Map<Integer, Double> semanticScores = safeMode == SearchMode.KEYWORD
                ? Collections.emptyMap()
                : scatterGatherSemantic(keyword.trim(), queryTerms);

        Map<Integer, Double> merged = mergeScores(keywordScores, semanticScores, safeMode);
        if (categoryId != null && categoryId > 0) {
            merged = filterByCategory(merged, categoryId);
        }
        log.info("[搜索协调器] mode={}, keyword={}, 命中={}, 语义引擎={}, 耗时={}ms, shards={}",
                safeMode, keyword, merged.size(), lastSemanticEngine,
                System.currentTimeMillis() - start, shardCount);
        return merged;
    }

    public String getLastSemanticEngine() {
        return lastSemanticEngine;
    }

    private Map<Integer, Double> scatterGatherKeyword(List<String> queryTerms) {
        Map<Integer, Double> scores = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        for (int shard = 0; shard < shardCount; shard++) {
            final int shardId = shard;
            futures.add(searchExecutor.submit(() -> {
                Map<Integer, Integer> hitCount = new HashMap<>();
                for (String term : queryTerms) {
                    String key = SearchShardRouter.productTermKey(shardId, term);
                    Set<Object> ids = redisTemplate.opsForSet().members(key);
                    if (ids == null) {
                        continue;
                    }
                    for (Object raw : ids) {
                        Integer pid = parseInt(raw, null);
                        if (pid != null) {
                            hitCount.merge(pid, 1, Integer::sum);
                        }
                    }
                }
                for (Map.Entry<Integer, Integer> e : hitCount.entrySet()) {
                    double s = TfIdfVectorUtil.keywordScore(
                            e.getValue(), queryTerms.size(), e.getValue());
                    scores.merge(e.getKey(), s, Math::max);
                }
            }));
        }
        awaitFutures(futures);
        return scores;
    }

    private Map<Integer, Double> scatterGatherSemantic(String rawQuery, List<String> queryTerms) {
        float[] queryEmbedding = embeddingService.embed(rawQuery);
        if (queryEmbedding != null && queryEmbedding.length > 0) {
            Map<Integer, Double> knn = redisStackVectorIndexService.knnSearch(queryEmbedding, null, 50);
            if (!knn.isEmpty()) {
                lastSemanticEngine = "redis-stack-knn/" + embeddingService.modelName();
                return knn;
            }
            Map<Integer, Double> dense = denseCosineOnCandidates(queryEmbedding, queryTerms);
            if (!dense.isEmpty()) {
                lastSemanticEngine = "search:vec-cosine/" + embeddingService.modelName();
                return dense;
            }
        }
        return scatterGatherSemanticTfidf(SemanticSynonymExpander.expand(queryTerms));
    }

    private Map<Integer, Double> denseCosineOnCandidates(float[] queryEmbedding, List<String> queryTerms) {
        Set<Integer> candidateIds = collectCandidateIds(SemanticSynonymExpander.expand(queryTerms));
        Map<Integer, Double> scores = new HashMap<>();
        for (Integer pid : candidateIds) {
            ProductVectorDocument doc = loadVectorDocument(pid);
            if (doc != null && doc.hasDenseEmbedding()) {
                double sim = EmbeddingVectorCodec.cosineSimilarity(queryEmbedding, doc.getEmbedding());
                if (sim > 0.05) {
                    scores.put(pid, sim);
                }
            }
        }
        return scores;
    }

    private Map<Integer, Double> scatterGatherSemanticTfidf(List<String> semanticTerms) {
        lastSemanticEngine = "tfidf-fallback";
        int totalDocs = getDocCount();
        Map<String, Integer> docFreq = loadDocFreq(semanticTerms);
        Map<String, Double> queryVec = TfIdfVectorUtil.buildQueryVector(semanticTerms, docFreq, totalDocs);
        if (queryVec.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Integer> candidateIds = collectCandidateIds(semanticTerms);
        Map<Integer, Double> scores = new HashMap<>();
        for (Integer pid : candidateIds) {
            ProductVectorDocument doc = loadVectorDocument(pid);
            Map<String, Double> docVec = doc != null && doc.getTfidf() != null
                    ? doc.getTfidf() : Collections.emptyMap();
            if (docVec.isEmpty()) {
                continue;
            }
            double sim = TfIdfVectorUtil.cosineSimilarity(queryVec, docVec);
            if (sim > 0.05) {
                scores.put(pid, sim);
            }
        }
        return scores;
    }

    private Set<Integer> collectCandidateIds(List<String> terms) {
        Set<Integer> candidateIds = new HashSet<>();
        for (int shard = 0; shard < shardCount; shard++) {
            for (String term : terms) {
                Set<Object> ids = redisTemplate.opsForSet().members(
                        SearchShardRouter.productTermKey(shard, term));
                if (ids == null) {
                    continue;
                }
                for (Object raw : ids) {
                    Integer pid = parseInt(raw, null);
                    if (pid != null) {
                        candidateIds.add(pid);
                    }
                }
            }
        }
        return candidateIds;
    }

    private Map<Integer, Double> mergeScores(Map<Integer, Double> keyword,
                                             Map<Integer, Double> semantic,
                                             SearchMode mode) {
        Set<Integer> allIds = new HashSet<>();
        if (keyword != null) {
            allIds.addAll(keyword.keySet());
        }
        if (semantic != null) {
            allIds.addAll(semantic.keySet());
        }
        Map<Integer, Double> merged = new HashMap<>();
        for (Integer id : allIds) {
            double kw = keyword != null ? keyword.getOrDefault(id, 0.0) : 0.0;
            double sem = semantic != null ? semantic.getOrDefault(id, 0.0) : 0.0;
            double score;
            if (mode == SearchMode.KEYWORD) {
                score = kw;
            } else if (mode == SearchMode.SEMANTIC) {
                score = sem;
            } else {
                score = kw * keywordWeight + sem * semanticWeight;
            }
            if (score > 0.001) {
                merged.put(id, score);
            }
        }
        return merged;
    }

    private Map<Integer, Double> filterByCategory(Map<Integer, Double> scores, int categoryId) {
        Map<Integer, Double> filtered = new HashMap<>();
        for (Map.Entry<Integer, Double> e : scores.entrySet()) {
            Map<Object, Object> doc = redisTemplate.opsForHash()
                    .entries(SearchShardRouter.productDocKey(e.getKey()));
            Integer cat = parseInt(doc.get("categoryId"), null);
            if (cat != null && cat == categoryId) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return filtered;
    }

    private String buildIndexText(Product product) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(product.getName())) {
            sb.append(product.getName()).append(' ');
        }
        if (StringUtils.hasText(product.getDescription())) {
            sb.append(product.getDescription());
        }
        if (product.getCategory() != null && StringUtils.hasText(product.getCategory().getCategoryName())) {
            sb.append(product.getCategory().getCategoryName());
        }
        return sb.toString().trim();
    }

    private List<String> buildIndexTerms(Product product) {
        String text = buildIndexText(product);
        List<String> keywords = productFeatureService.extractKeywords(text);
        Set<String> unique = new LinkedHashSet<>(keywords);
        unique.addAll(productFeatureService.tokenizeTitle(text));
        return new ArrayList<>(unique);
    }

    private void saveVectorDocument(int productId, ProductVectorDocument doc) throws Exception {
        String json = objectMapper.writeValueAsString(doc);
        redisTemplate.opsForValue().set(SearchShardRouter.productVectorKey(productId), json);
        redisTemplate.expire(SearchShardRouter.productVectorKey(productId),
                indexTtlSeconds, TimeUnit.SECONDS);
    }

    private ProductVectorDocument loadVectorDocument(Integer productId) {
        try {
            Object raw = redisTemplate.opsForValue().get(SearchShardRouter.productVectorKey(productId));
            if (raw == null) {
                return null;
            }
            String json = String.valueOf(raw);
            if (json.startsWith("{")) {
                return objectMapper.readValue(json, ProductVectorDocument.class);
            }
            Map<String, Double> legacy = objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
            return ProductVectorDocument.tfidfOnly(legacy);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Integer> loadDocFreq(List<String> terms) {
        Map<String, Integer> df = new HashMap<>();
        for (String term : terms) {
            Object v = redisTemplate.opsForValue().get(SearchShardRouter.docFreqKey(term));
            df.put(term, parseInt(v, 1));
        }
        return df;
    }

    private int getDocCount() {
        return parseInt(redisTemplate.opsForValue().get(SearchShardRouter.metaDocCountKey()), 1);
    }

    private int registerIndexedDoc(int productId) {
        redisTemplate.opsForSet().add("search:docs", productId);
        return refreshDocCount();
    }

    private int refreshDocCount() {
        Long size = redisTemplate.opsForSet().size("search:docs");
        int count = size == null || size <= 0 ? 1 : size.intValue();
        redisTemplate.opsForValue().set(SearchShardRouter.metaDocCountKey(), count);
        return count;
    }

    private void clearSearchKeys() {
        Set<String> keys = redisTemplate.keys("idx:product:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        Set<String> vecKeys = redisTemplate.keys("search:*");
        if (vecKeys != null && !vecKeys.isEmpty()) {
            redisTemplate.delete(vecKeys);
        }
        redisTemplate.delete(SearchShardRouter.metaDocCountKey());
    }

    private void awaitFutures(List<Future<?>> futures) {
        for (Future<?> f : futures) {
            try {
                f.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[搜索协调器] 分片查询超时: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseTerms(Object raw) {
        if (raw instanceof List) {
            return ((List<?>) raw).stream().map(String::valueOf).collect(Collectors.toList());
        }
        if (raw instanceof String) {
            String s = (String) raw;
            if (s.startsWith("[")) {
                try {
                    return new ObjectMapper().readValue(s, new TypeReference<List<String>>() {});
                } catch (Exception ignored) {
                    return Collections.emptyList();
                }
            }
        }
        return Collections.emptyList();
    }

    private static Integer parseInt(Object raw, Integer defaultVal) {
        if (raw == null) {
            return defaultVal;
        }
        if (raw instanceof Integer) {
            return (Integer) raw;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
