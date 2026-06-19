package com.campus.search.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 离线稠密向量：字符 n-gram + 哈希投影（无外部 API，维度与 Redis Stack 索引一致）
 */
@Component
@PropertySource("classpath:redis-config.properties")
public class LocalNgramEmbeddingService implements EmbeddingService {

    @Value("${search.embedding.dimensions:768}")
    private int dimensions;

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        float[] vec = new float[dimensions];
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        addNgrams(normalized, vec, 2);
        addNgrams(normalized, vec, 3);
        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            int h = stableHash(token);
            vec[Math.floorMod(h, dimensions)] += 1.0f;
        }
        return EmbeddingVectorCodec.normalize(vec);
    }

    private static void addNgrams(String text, float[] vec, int n) {
        for (int i = 0; i + n <= text.length(); i++) {
            String gram = text.substring(i, i + n);
            int h = stableHash(gram);
            vec[Math.floorMod(h, vec.length)] += 1.0f;
        }
    }

    private static int stableHash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
        } catch (Exception e) {
            return s.hashCode();
        }
    }

    @Override
    public String modelName() {
        return "local-ngram-" + dimensions;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
