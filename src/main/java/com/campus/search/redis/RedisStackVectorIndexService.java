package com.campus.search.redis;

import com.campus.entity.Product;
import com.campus.search.embedding.EmbeddingVectorCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;

import java.util.*;

/**
 * Redis Stack / RediSearch 向量索引（HNSW KNN）
 */
@Service
@PropertySource("classpath:redis-config.properties")
public class RedisStackVectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(RedisStackVectorIndexService.class);

    public static final String INDEX_NAME = "idx:product_emb";
    public static final String HASH_PREFIX = "search:emb:";

    @Value("${search.redis-stack.enabled:true}")
    private boolean stackEnabled;

    @Value("${search.embedding.dimensions:384}")
    private int dimensions;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private volatile Boolean stackAvailable;
    private volatile long lastProbeMs;

    public boolean isStackAvailable() {
        if (!stackEnabled || redisTemplate == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (stackAvailable != null && now - lastProbeMs < 60_000L) {
            return stackAvailable;
        }
        lastProbeMs = now;
        stackAvailable = probeRediSearch();
        return stackAvailable;
    }

    public void ensureIndex() {
        if (!isStackAvailable()) {
            return;
        }
        try {
            List<Object> existing = ftList();
            if (existing != null) {
                for (Object name : existing) {
                    if (INDEX_NAME.equals(String.valueOf(name))) {
                        return;
                    }
                }
            }
            send("FT.CREATE", INDEX_NAME,
                    "ON", "HASH",
                    "PREFIX", "1", HASH_PREFIX,
                    "SCHEMA",
                    "product_id", "NUMERIC", "SORTABLE",
                    "category_id", "NUMERIC",
                    "name", "TEXT",
                    "description", "TEXT",
                    "embedding", "VECTOR", "HNSW", "6",
                    "TYPE", "FLOAT32",
                    "DIM", String.valueOf(dimensions),
                    "DISTANCE_METRIC", "COSINE");
            log.info("[RedisStack] 已创建向量索引 {} dim={}", INDEX_NAME, dimensions);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Index already exists")) {
                return;
            }
            log.warn("[RedisStack] 创建索引失败: {}", msg);
            stackAvailable = false;
        }
    }

    public void upsertProduct(Product product, float[] embedding) {
        if (product == null || product.getId() == null || embedding == null || embedding.length == 0
                || redisTemplate == null) {
            return;
        }
        String key = HASH_PREFIX + product.getId();
        byte[] keyBytes = SafeEncoder.encode(key);
        byte[] blob = EmbeddingVectorCodec.toFloat32Bytes(embedding);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.hSet(keyBytes, SafeEncoder.encode("product_id"),
                    SafeEncoder.encode(String.valueOf(product.getId())));
            connection.hSet(keyBytes, SafeEncoder.encode("category_id"),
                    SafeEncoder.encode(String.valueOf(product.getCategoryId() != null ? product.getCategoryId() : 0)));
            connection.hSet(keyBytes, SafeEncoder.encode("name"),
                    SafeEncoder.encode(product.getName() != null ? product.getName() : ""));
            connection.hSet(keyBytes, SafeEncoder.encode("description"),
                    SafeEncoder.encode(product.getDescription() != null ? product.getDescription() : ""));
            connection.hSet(keyBytes, SafeEncoder.encode("embedding"), blob);
            return null;
        });
        if (isStackAvailable()) {
            ensureIndex();
        }
    }

    public void deleteProduct(int productId) {
        if (redisTemplate != null) {
            redisTemplate.delete(HASH_PREFIX + productId);
        }
    }

    public Map<Integer, Double> knnSearch(float[] queryVector, Integer categoryId, int k) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (!isStackAvailable() || queryVector == null || queryVector.length == 0) {
            return result;
        }
        ensureIndex();
        try {
            final byte[] blob = EmbeddingVectorCodec.toFloat32Bytes(queryVector);
            String query = "*=>[KNN " + k + " @embedding $vec AS vector_score]";
            if (categoryId != null && categoryId > 0) {
                query = "@category_id:[" + categoryId + " " + categoryId + "]=>[KNN " + k
                        + " @embedding $vec AS vector_score]";
            }
            final String knnQuery = query;
            List<Object> raw = redisTemplate.execute((RedisCallback<List<Object>>) connection -> {
                Jedis jedis = getJedis(connection);
                if (jedis == null) {
                    throw new IllegalStateException("Jedis unavailable");
                }
                return (List<Object>) jedis.sendCommand(Cmd.of("FT.SEARCH"),
                        SafeEncoder.encode(INDEX_NAME),
                        SafeEncoder.encode(knnQuery),
                        SafeEncoder.encode("PARAMS"),
                        SafeEncoder.encode("2"),
                        SafeEncoder.encode("vec"),
                        blob,
                        SafeEncoder.encode("DIALECT"),
                        SafeEncoder.encode("2"),
                        SafeEncoder.encode("RETURN"),
                        SafeEncoder.encode("2"),
                        SafeEncoder.encode("product_id"),
                        SafeEncoder.encode("vector_score"),
                        SafeEncoder.encode("SORTBY"),
                        SafeEncoder.encode("vector_score"),
                        SafeEncoder.encode("ASC"));
            });
            parseKnnResult(raw, result);
        } catch (Exception e) {
            log.warn("[RedisStack] KNN 检索失败: {}", e.getMessage());
        }
        return result;
    }

    public void dropIndex() {
        if (!isStackAvailable()) {
            return;
        }
        try {
            send("FT.DROPINDEX", INDEX_NAME, "DD");
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void parseKnnResult(List<Object> raw, Map<Integer, Double> out) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        long total = parseLong(raw.get(0), 0);
        if (total <= 0) {
            return;
        }
        for (int i = 1; i < raw.size(); i += 2) {
            if (i + 1 >= raw.size()) {
                break;
            }
            Object fieldsObj = raw.get(i + 1);
            if (!(fieldsObj instanceof List)) {
                continue;
            }
            List<Object> fields = (List<Object>) fieldsObj;
            Integer productId = null;
            Double score = null;
            for (int j = 0; j + 1 < fields.size(); j += 2) {
                String field = String.valueOf(fields.get(j));
                String val = String.valueOf(fields.get(j + 1));
                if ("product_id".equals(field)) {
                    productId = parseInt(val, null);
                } else if ("vector_score".equals(field)) {
                    double dist = parseDouble(val, 1.0);
                    score = Math.max(0.0, 1.0 - dist);
                }
            }
            if (productId != null && score != null) {
                out.put(productId, score);
            }
        }
    }

    private boolean probeRediSearch() {
        try {
            ftList();
            return true;
        } catch (Exception e) {
            log.info("[RedisStack] 未检测到 RediSearch，语义检索使用 search:vec 稠密余弦: {}",
                    e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> ftList() {
        return send("FT._LIST");
    }

    @SuppressWarnings("unchecked")
    private List<Object> send(String command, String... args) {
        if (redisTemplate == null) {
            return Collections.emptyList();
        }
        return redisTemplate.execute((RedisCallback<List<Object>>) connection -> {
            Jedis jedis = getJedis(connection);
            if (jedis == null) {
                throw new IllegalStateException("Jedis unavailable");
            }
            byte[][] encoded = new byte[args.length][];
            for (int i = 0; i < args.length; i++) {
                encoded[i] = SafeEncoder.encode(args[i]);
            }
            return (List<Object>) jedis.sendCommand(Cmd.of(command), encoded);
        });
    }

    private static Jedis getJedis(RedisConnection connection) {
        Object nativeConnection = connection.getNativeConnection();
        if (nativeConnection instanceof Jedis) {
            return (Jedis) nativeConnection;
        }
        return null;
    }

    private static final class Cmd implements ProtocolCommand {
        private final byte[] raw;

        private Cmd(String command) {
            this.raw = SafeEncoder.encode(command);
        }

        static Cmd of(String command) {
            return new Cmd(command);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    private static long parseLong(Object raw, long def) {
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception e) {
            return def;
        }
    }

    private static Integer parseInt(String raw, Integer def) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return def;
        }
    }

    private static double parseDouble(String raw, double def) {
        try {
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return def;
        }
    }
}
