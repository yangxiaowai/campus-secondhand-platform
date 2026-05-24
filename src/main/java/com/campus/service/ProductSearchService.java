package com.campus.service;

import com.campus.search.SearchMode;
import com.campus.search.SearchPageResult;

/**
 * 商品搜索服务（关键词 + 语义 + 混合，含分布式降级）
 */
public interface ProductSearchService {

    SearchPageResult search(String keyword, Integer categoryId, SearchMode mode,
                            int pageNum, int pageSize);
}
