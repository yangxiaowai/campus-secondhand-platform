package com.campus.search.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 按优先级选择 Embedding 提供方：Ollama → OpenAI 兼容 → 本地 n-gram
 */
@Service
@Primary
public class CompositeEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(CompositeEmbeddingService.class);

    @Autowired
    private OllamaEmbeddingService ollamaEmbeddingService;

    @Autowired
    private OpenAiCompatibleEmbeddingService openAiCompatibleEmbeddingService;

    @Autowired
    private LocalNgramEmbeddingService localNgramEmbeddingService;

    private volatile String lastProvider = "local-ngram";

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        float[] vec = tryEmbed(ollamaEmbeddingService, text);
        if (vec != null) {
            lastProvider = ollamaEmbeddingService.modelName();
            return vec;
        }
        vec = tryEmbed(openAiCompatibleEmbeddingService, text);
        if (vec != null) {
            lastProvider = openAiCompatibleEmbeddingService.modelName();
            return vec;
        }
        vec = localNgramEmbeddingService.embed(text);
        lastProvider = localNgramEmbeddingService.modelName();
        return vec;
    }

    private static float[] tryEmbed(EmbeddingService service, String text) {
        if (service != null && service.isAvailable()) {
            return service.embed(text);
        }
        return null;
    }

    @Override
    public String modelName() {
        return lastProvider;
    }

    @Override
    public int dimensions() {
        return localNgramEmbeddingService.dimensions();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    public String activeProvider() {
        return lastProvider;
    }
}
