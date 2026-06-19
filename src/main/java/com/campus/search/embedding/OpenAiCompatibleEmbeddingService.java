package com.campus.search.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * OpenAI 兼容 Embedding：POST {base}/embeddings
 */
@Component
@PropertySource("classpath:redis-config.properties")
public class OpenAiCompatibleEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingService.class);

    @Value("${search.embedding.openai.enabled:false}")
    private boolean enabled;

    @Value("${search.embedding.openai.base-url:}")
    private String baseUrl;

    @Value("${search.embedding.openai.api-key:}")
    private String apiKey;

    @Value("${search.embedding.openai.model:text-embedding-3-small}")
    private String model;

    @Value("${search.embedding.dimensions:768}")
    private int dimensions;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile long lastFailMs;
    private static final long COOLDOWN_MS = 30_000L;

    @Override
    public float[] embed(String text) {
        if (!isAvailable() || !StringUtils.hasText(text)) {
            return null;
        }
        try {
            String body = objectMapper.createObjectNode()
                    .put("model", model)
                    .put("input", text.trim())
                    .toString();
            String url = baseUrl.replaceAll("/$", "") + "/embeddings";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            JsonNode root = objectMapper.readTree(in);
            conn.disconnect();
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode emb = data.get(0).path("embedding");
                if (emb.isArray()) {
                    float[] vec = new float[emb.size()];
                    for (int i = 0; i < emb.size(); i++) {
                        vec[i] = (float) emb.get(i).asDouble();
                    }
                    return EmbeddingVectorCodec.normalize(vec);
                }
            }
        } catch (Exception e) {
            lastFailMs = System.currentTimeMillis();
            log.warn("[Embedding-OpenAI] 调用失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public String modelName() {
        return "openai/" + model;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public boolean isAvailable() {
        if (!enabled || !StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey)) {
            return false;
        }
        return System.currentTimeMillis() - lastFailMs > COOLDOWN_MS;
    }
}
