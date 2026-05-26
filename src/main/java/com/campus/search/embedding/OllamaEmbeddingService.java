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
import java.util.Locale;

/**
 * Ollama Embedding API：POST /api/embeddings
 */
@Component
@PropertySource("classpath:redis-config.properties")
public class OllamaEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    @Value("${search.embedding.ollama.enabled:false}")
    private boolean enabled;

    @Value("${search.embedding.ollama.base-url:http://127.0.0.1:11434}")
    private String baseUrl;

    @Value("${search.embedding.ollama.model:nomic-embed-text}")
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
                    .put("prompt", text.trim())
                    .toString();
            String url = baseUrl.replaceAll("/$", "") + "/api/embeddings";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            JsonNode root = objectMapper.readTree(in);
            conn.disconnect();
            if (root.has("embedding") && root.get("embedding").isArray()) {
                JsonNode arr = root.get("embedding");
                float[] vec = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    vec[i] = (float) arr.get(i).asDouble();
                }
                return EmbeddingVectorCodec.normalize(vec);
            }
        } catch (Exception e) {
            lastFailMs = System.currentTimeMillis();
            log.warn("[Embedding-Ollama] 调用失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public String modelName() {
        return "ollama/" + model;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        return System.currentTimeMillis() - lastFailMs > COOLDOWN_MS;
    }
}
