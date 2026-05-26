package com.campus.controller;

import com.campus.config.MinIOConfig;
import com.campus.entity.Product;
import com.campus.service.DegradeService;
import com.campus.service.IndexService;
import com.campus.service.ProductSearchIndexService;
import com.campus.service.ProductSearchService;
import com.campus.search.SearchMode;
import com.campus.search.SearchPageResult;
import com.campus.search.SearchRecommendCriteria;
import com.campus.search.embedding.EmbeddingService;
import com.campus.search.redis.RedisStackVectorIndexService;
import com.campus.service.MetricsService;
import com.campus.service.RecommendService;
import io.minio.MinioClient;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证工具 Controller
 * 成员1：用于验证 Redis 连接和分布式 Session 是否正常工作
 * 
 * 验收方法：
 * 1. 访问 /test/redis → 返回 Redis 连接状态 ✅
 * 2. 访问 /test/session/set?value=hello → 在 Session 中存入数据 ✅
 * 3. 访问 /test/session/get → 读取 Session 中的数据 ✅
 *    （在实例A存入，在实例B读取，验证分布式Session）
 * 4. 访问 /test/minio → 返回 MinIO 连接状态 ✅
 */
@Controller
@RequestMapping("/test")
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private MinioClient minioClient;

    @Autowired(required = false)
    private MinIOConfig minIOConfig;

    @Autowired(required = false)
    private RecommendService recommendService;

    @Autowired(required = false)
    private DegradeService degradeService;

    @Autowired(required = false)
    private MetricsService metricsService;

    @Autowired(required = false)
    private IndexService indexService;

    @Autowired(required = false)
    private ProductSearchService productSearchService;

    @Autowired(required = false)
    private ProductSearchIndexService productSearchIndexService;

    @Autowired(required = false)
    private EmbeddingService embeddingService;

    @Autowired(required = false)
    private RedisStackVectorIndexService redisStackVectorIndexService;

    /**
     * 测试 Redis 连接
     */
    @GetMapping("/redis")
    @ResponseBody
    public Map<String, Object> testRedis() {
        Map<String, Object> result = new HashMap<>();
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set("test:ping", "pong");
                String value = (String) redisTemplate.opsForValue().get("test:ping");
                result.put("success", true);
                result.put("message", "Redis 连接正常");
                result.put("data", "写入 test:ping = " + value);
            } else {
                result.put("success", false);
                result.put("message", "RedisTemplate 未注入，请检查 Redis 配置");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Redis 连接失败: " + e.getMessage());
            log.error("Redis 测试失败", e);
        }
        return result;
    }

    /**
     * 测试分布式 Session - 存入数据
     */
    @GetMapping("/session/set")
    @ResponseBody
    public Map<String, Object> setSession(String value, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        if (value == null) {
            value = "默认测试值";
        }
        session.setAttribute("testValue", value);
        session.setAttribute("testTime", System.currentTimeMillis());
        result.put("success", true);
        result.put("message", "Session 写入成功");
        result.put("sessionId", session.getId());
        result.put("value", value);
        return result;
    }

    /**
     * 测试分布式 Session - 读取数据
     */
    @GetMapping("/session/get")
    @ResponseBody
    public Map<String, Object> getSession(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Object testValue = session.getAttribute("testValue");
        Object testTime = session.getAttribute("testTime");
        result.put("success", true);
        result.put("sessionId", session.getId());
        result.put("testValue", testValue);
        result.put("testTime", testTime);
        result.put("message", testValue != null ? "Session 读取成功，数据跨实例共享！" : "Session 中无数据，请先访问 /test/session/set");
        return result;
    }

    /**
     * 测试 MinIO 连接
     */
    @GetMapping("/minio")
    @ResponseBody
    public Map<String, Object> testMinIO() {
        Map<String, Object> result = new HashMap<>();
        try {
            if (minioClient != null && minIOConfig != null) {
                String bucketName = minIOConfig.getBucketName();
                boolean found = minioClient.bucketExists(
                        io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
                result.put("success", true);
                result.put("message", "MinIO 连接正常");
                result.put("bucket", bucketName);
                result.put("bucketExists", found);
                result.put("endpoint", minIOConfig.getEndpoint());

                // 列出 bucket 中的文件
                if (found) {
                    Iterable<Result<Item>> objects = minioClient.listObjects(
                            ListObjectsArgs.builder().bucket(bucketName).build());
                    int count = 0;
                    for (@SuppressWarnings("unused") Result<Item> item : objects) {
                        count++;
                    }
                    result.put("fileCount", count);
                }
            } else {
                result.put("success", false);
                result.put("message", "MinIO 客户端未注入，请检查 MinIO 配置");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "MinIO 连接失败: " + e.getMessage());
            log.error("MinIO 测试失败", e);
        }
        return result;
    }

    /**
     * 成员1-阶段2：模拟浏览行为，测试分布式锁+缓存更新。
     */
    @GetMapping("/recommend/record")
    @ResponseBody
    public Map<String, Object> testRecord(Integer userId, Integer productId) {
        Map<String, Object> result = new HashMap<>();
        if (recommendService == null) {
            result.put("success", false);
            result.put("message", "RecommendService 未注入");
            return result;
        }
        if (userId == null || productId == null) {
            result.put("success", false);
            result.put("message", "请传 userId 和 productId");
            return result;
        }
        recommendService.recordBrowseHistory(userId, productId);
        result.put("success", true);
        result.put("message", "浏览记录写入成功");
        result.put("userId", userId);
        result.put("productId", productId);
        return result;
    }

    /**
     * 成员1-阶段2：读取推荐结果，触发画像缓存重建。
     */
    @GetMapping("/recommend/list")
    @ResponseBody
    public Map<String, Object> testRecommend(Integer userId, Integer limit) {
        Map<String, Object> result = new HashMap<>();
        if (recommendService == null) {
            result.put("success", false);
            result.put("message", "RecommendService 未注入");
            return result;
        }
        if (userId == null) {
            result.put("success", false);
            result.put("message", "请传 userId");
            return result;
        }
        int safeLimit = (limit == null || limit <= 0) ? 5 : limit;
        List<Product> data;
        if (degradeService != null) {
            data = degradeService.recommendForUser(userId, safeLimit);
            result.put("redisAvailable", degradeService.isRedisAvailable());
        } else {
            data = recommendService.getPersonalizedRecommendations(userId, safeLimit);
        }
        result.put("success", true);
        result.put("count", data.size());
        result.put("data", data);
        return result;
    }

    /**
     * 成员D：降级推荐验收（含 L1/L2）
     */
    @GetMapping("/degrade/recommend")
    @ResponseBody
    public Map<String, Object> testDegradeRecommend(Integer userId, Integer limit) {
        Map<String, Object> result = new HashMap<>();
        if (degradeService == null) {
            result.put("success", false);
            result.put("message", "DegradeService 未注入");
            return result;
        }
        int safeLimit = (limit == null || limit <= 0) ? 8 : limit;
        List<Product> data = degradeService.recommendForUser(userId, safeLimit);
        result.put("success", true);
        result.put("redisAvailable", degradeService.isRedisAvailable());
        result.put("count", data.size());
        result.put("data", data);
        return result;
    }

    /**
     * 成员D：指标快照
     */
    @GetMapping("/degrade/metrics")
    @ResponseBody
    public Map<String, Object> testDegradeMetrics() {
        Map<String, Object> result = new HashMap<>();
        if (metricsService == null) {
            result.put("success", false);
            result.put("message", "MetricsService 未注入");
            return result;
        }
        result.put("success", true);
        result.putAll(metricsService.snapshot());
        return result;
    }

    /**
     * 成员1-阶段2：查看 Redis 中的画像和历史缓存。
     */
    @GetMapping("/recommend/cache")
    @ResponseBody
    public Map<String, Object> testRecommendCache(Integer userId) {
        Map<String, Object> result = new HashMap<>();
        if (redisTemplate == null) {
            result.put("success", false);
            result.put("message", "RedisTemplate 未注入");
            return result;
        }
        if (userId == null) {
            result.put("success", false);
            result.put("message", "请传 userId");
            return result;
        }
        String profileKey = "rec:profile:" + userId;
        String historyKey = "rec:history:" + userId;
        result.put("success", true);
        result.put("profileKey", profileKey);
        result.put("profile", redisTemplate.opsForHash().entries(profileKey));
        result.put("profileTTL", redisTemplate.getExpire(profileKey));
        result.put("historyKey", historyKey);
        result.put("history", redisTemplate.opsForList().range(historyKey, 0, 20));
        result.put("historyTTL", redisTemplate.getExpire(historyKey));
        return result;
    }

    /**
     * 成员4：查看两级索引与收件箱（验收用）
     */
    @GetMapping("/index/stats")
    @ResponseBody
    public Map<String, Object> testIndexStats(Integer categoryId, String keyword, Integer userId) {
        Map<String, Object> result = new HashMap<>();
        if (redisTemplate == null) {
            result.put("success", false);
            result.put("message", "RedisTemplate 未注入");
            return result;
        }
        int catId = categoryId == null ? 1 : categoryId;
        String kw = (keyword == null || keyword.isEmpty()) ? "算法" : keyword;
        String categoryKey = "idx:category:" + catId + ":users";
        String keywordKey = "idx:keyword:" + kw + ":users";
        result.put("success", true);
        result.put("categoryKey", categoryKey);
        result.put("categoryUsers", redisTemplate.opsForSet().members(categoryKey));
        result.put("keywordKey", keywordKey);
        result.put("keywordUsers", redisTemplate.opsForSet().members(keywordKey));
        if (userId != null) {
            String inboxKey = "user:inbox:" + userId;
            result.put("inboxKey", inboxKey);
            result.put("inbox", redisTemplate.opsForZSet().reverseRangeWithScores(inboxKey, 0, 19));
        }
        return result;
    }

    /**
     * 成员4：触发全量索引重建
     */
    @GetMapping("/index/rebuild")
    @ResponseBody
    public Map<String, Object> testIndexRebuild() {
        Map<String, Object> result = new HashMap<>();
        if (indexService == null) {
            result.put("success", false);
            result.put("message", "IndexService 未注入");
            return result;
        }
        indexService.rebuildAllIndexes();
        result.put("success", true);
        result.put("message", "全量索引重建已触发，请访问 /test/index/stats 查看");
        return result;
    }

    /**
     * 商品搜索验收：关键词 / 语义 / 混合
     */
    @GetMapping("/search")
    @ResponseBody
    public Map<String, Object> testSearch(String keyword,
                                          Integer categoryId,
                                          String mode,
                                          Integer pageNum,
                                          Integer pageSize,
                                          Double minPrice,
                                          Double maxPrice,
                                          Integer maxPublishDays,
                                          String sortBy,
                                          Integer userId) {
        Map<String, Object> result = new HashMap<>();
        if (productSearchService == null) {
            result.put("success", false);
            result.put("message", "ProductSearchService 未注入");
            return result;
        }
        if (keyword == null || keyword.isEmpty()) {
            result.put("success", false);
            result.put("message", "请传 keyword 参数");
            return result;
        }
        SearchMode searchMode = SearchMode.from(mode);
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 12 : pageSize;
        SearchRecommendCriteria criteria = new SearchRecommendCriteria();
        criteria.setMinPrice(minPrice);
        criteria.setMaxPrice(maxPrice);
        criteria.setMaxPublishDays(maxPublishDays);
        criteria.setSortBy(SearchRecommendCriteria.parseSortBy(sortBy));
        criteria.setUserId(userId);
        SearchPageResult page = productSearchService.search(keyword, categoryId, searchMode, pn, ps, criteria);
        result.put("success", true);
        result.put("sortBy", criteria.getSortBy().name());
        result.put("rankScores", page.getRankScores());
        result.put("searchMode", page.getSearchMode().name());
        result.put("engine", page.getEngine());
        result.put("degradeLevel", page.getDegradeLevel());
        result.put("tookMs", page.getTookMs());
        result.put("shardCount", page.getShardCount());
        result.put("total", page.getTotal());
        result.put("count", page.getList().size());
        result.put("data", page.getList());
        result.put("scores", page.getScores());
        result.put("semanticEngine", page.getSemanticEngine());
        result.put("embeddingModel", page.getEmbeddingModel());
        if (degradeService != null) {
            result.put("redisAvailable", degradeService.isRedisAvailable());
        }
        if (redisStackVectorIndexService != null) {
            result.put("redisStackAvailable", redisStackVectorIndexService.isStackAvailable());
        }
        return result;
    }

    /**
     * 测试 Embedding 生成
     */
    @GetMapping("/search/embedding")
    @ResponseBody
    public Map<String, Object> testEmbedding(String text) {
        Map<String, Object> result = new HashMap<>();
        if (embeddingService == null) {
            result.put("success", false);
            result.put("message", "EmbeddingService 未注入");
            return result;
        }
        if (text == null || text.isEmpty()) {
            text = "二手教材 算法导论";
        }
        float[] vec = embeddingService.embed(text);
        result.put("success", vec != null);
        result.put("model", embeddingService.modelName());
        result.put("dimensions", vec != null ? vec.length : 0);
        result.put("sample", vec != null && vec.length > 0
                ? Arrays.asList(vec[0], vec[Math.min(1, vec.length - 1)], vec[Math.min(2, vec.length - 1)])
                : Collections.emptyList());
        if (redisStackVectorIndexService != null) {
            result.put("redisStackAvailable", redisStackVectorIndexService.isStackAvailable());
        }
        return result;
    }

    /**
     * 重建商品搜索倒排索引
     */
    @GetMapping("/search/rebuild")
    @ResponseBody
    public Map<String, Object> testSearchRebuild() {
        Map<String, Object> result = new HashMap<>();
        if (productSearchIndexService == null) {
            result.put("success", false);
            result.put("message", "ProductSearchIndexService 未注入");
            return result;
        }
        productSearchIndexService.rebuildAllIndexes();
        result.put("success", true);
        result.put("message", "商品搜索索引全量重建完成，请访问 /test/search?keyword=教材 验收");
        return result;
    }
}
