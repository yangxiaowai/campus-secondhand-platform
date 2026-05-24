package com.campus.search;

import com.campus.entity.Product;
import com.github.pagehelper.PageInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 搜索结果（含分布式搜索元信息）
 */
public class SearchPageResult {

    private final List<Product> list;
    private final int pageNum;
    private final int pageSize;
    private final long total;
    private final int pages;
    private final boolean hasPreviousPage;
    private final boolean hasNextPage;
    private final int prePage;
    private final int nextPage;
    private final int[] navigatepageNums;

    private final SearchMode searchMode;
    private final String engine;
    private final String degradeLevel;
    private final long tookMs;
    private final int shardCount;
    private final Map<Integer, Double> scores;
    private final String semanticEngine;
    private final String embeddingModel;

    public SearchPageResult(List<Product> list, int pageNum, int pageSize, long total,
                            SearchMode searchMode, String engine, String degradeLevel,
                            long tookMs, int shardCount, Map<Integer, Double> scores,
                            String semanticEngine, String embeddingModel) {
        this.list = list != null ? list : Collections.emptyList();
        this.pageNum = Math.max(1, pageNum);
        this.pageSize = Math.max(1, pageSize);
        this.total = Math.max(0, total);
        this.pages = this.pageSize == 0 ? 0 : (int) ((this.total + this.pageSize - 1) / this.pageSize);
        this.hasPreviousPage = this.pageNum > 1;
        this.hasNextPage = this.pageNum < this.pages;
        this.prePage = hasPreviousPage ? this.pageNum - 1 : 1;
        this.nextPage = hasNextPage ? this.pageNum + 1 : this.pages;
        this.navigatepageNums = buildNavigate(this.pageNum, this.pages);
        this.searchMode = searchMode != null ? searchMode : SearchMode.HYBRID;
        this.engine = engine != null ? engine : "mysql";
        this.degradeLevel = degradeLevel != null ? degradeLevel : "none";
        this.tookMs = tookMs;
        this.shardCount = shardCount;
        this.scores = scores;
        this.semanticEngine = semanticEngine != null ? semanticEngine : "";
        this.embeddingModel = embeddingModel != null ? embeddingModel : "";
    }

    public SearchPageResult(List<Product> list, int pageNum, int pageSize, long total,
                            SearchMode searchMode, String engine, String degradeLevel,
                            long tookMs, int shardCount, Map<Integer, Double> scores) {
        this(list, pageNum, pageSize, total, searchMode, engine, degradeLevel, tookMs, shardCount, scores, "", "");
    }

    public static SearchPageResult fromPageInfo(PageInfo<Product> pageInfo, SearchMode mode,
                                                String engine, String degradeLevel) {
        return new SearchPageResult(
                pageInfo.getList(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                mode,
                engine,
                degradeLevel,
                0L,
                0,
                Collections.emptyMap(),
                "",
                ""
        );
    }

    private static int[] buildNavigate(int current, int totalPages) {
        if (totalPages <= 0) {
            return new int[0];
        }
        int start = Math.max(1, current - 2);
        int end = Math.min(totalPages, start + 4);
        start = Math.max(1, end - 4);
        int len = end - start + 1;
        int[] nums = new int[len];
        for (int i = 0; i < len; i++) {
            nums[i] = start + i;
        }
        return nums;
    }

    public List<Product> getList() {
        return list;
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotal() {
        return total;
    }

    public int getPages() {
        return pages;
    }

    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }

    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public int getPrePage() {
        return prePage;
    }

    public int getNextPage() {
        return nextPage;
    }

    public int[] getNavigatepageNums() {
        return navigatepageNums;
    }

    public SearchMode getSearchMode() {
        return searchMode;
    }

    public String getEngine() {
        return engine;
    }

    public String getDegradeLevel() {
        return degradeLevel;
    }

    public long getTookMs() {
        return tookMs;
    }

    public int getShardCount() {
        return shardCount;
    }

    public Map<Integer, Double> getScores() {
        return scores;
    }

    public String getSemanticEngine() {
        return semanticEngine;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }
}
