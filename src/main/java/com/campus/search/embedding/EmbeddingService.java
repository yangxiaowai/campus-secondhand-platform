package com.campus.search.embedding;

/**
 * 文本 Embedding 服务（Ollama / OpenAI 兼容 / 本地降级）
 */
public interface EmbeddingService {

    /**
     * @return 归一化后的稠密向量；失败时返回 null
     */
    float[] embed(String text);

    String modelName();

    int dimensions();

    boolean isAvailable();
}
