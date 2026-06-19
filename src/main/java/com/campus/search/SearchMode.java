package com.campus.search;

/**
 * 搜索模式：关键词倒排 / 语义向量 / 混合
 */
public enum SearchMode {
    KEYWORD,
    SEMANTIC,
    HYBRID;

    public static SearchMode from(String raw) {
        if (raw == null || raw.isEmpty()) {
            return HYBRID;
        }
        try {
            return SearchMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HYBRID;
        }
    }
}
