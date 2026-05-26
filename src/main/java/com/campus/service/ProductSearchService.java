package com.campus.service;

import com.campus.search.SearchMode;
import com.campus.search.SearchPageResult;
import com.campus.search.SearchRecommendCriteria;

/**
 * 商品搜索服务（关键词 + 语义 + 混合，含分布式降级）
 */
public interface ProductSearchService {

    SearchPageResult search(String keyword, Integer categoryId, SearchMode mode,
                            int pageNum, int pageSize);

    /**
     * 搜索并在结果集上按时效、价格、画像做二次排序
     */
    SearchPageResult search(String keyword, Integer categoryId, SearchMode mode,
                            int pageNum, int pageSize, SearchRecommendCriteria criteria);
}
