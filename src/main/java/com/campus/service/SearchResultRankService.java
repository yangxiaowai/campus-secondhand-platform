package com.campus.service;

import com.campus.entity.Product;
import com.campus.search.RankedProduct;
import com.campus.search.SearchRecommendCriteria;

import java.util.List;
import java.util.Map;

/**
 * 搜索结果二次排序：时效性、价格区间、用户画像匹配
 */
public interface SearchResultRankService {

    /**
     * 按综合分重排（仅排除非在售；价格/时效/画像只影响排序，不隐藏商品）
     *
     * @param products         候选商品（需含 createTime、price）
     * @param relevanceScores  搜索相关度（商品ID → 分，无则空 Map）
     * @param searchKeyword    当前搜索词（用于关键词匹配，可空）
     * @param criteria         约束与排序方式
     */
    List<RankedProduct> filterAndRank(List<Product> products,
                                      Map<Integer, Double> relevanceScores,
                                      String searchKeyword,
                                      SearchRecommendCriteria criteria);
}
