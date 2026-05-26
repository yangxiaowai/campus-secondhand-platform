package com.campus.search;

import java.math.BigDecimal;

/**
 * 搜索推荐列表约束与排序偏好
 */
public class SearchRecommendCriteria {

    public enum SortBy {
        /** 综合：相关度 + 画像匹配 + 时效 + 价格契合 */
        BEST_FIT,
        /** 仅按发布时间 */
        NEWEST,
        /** 价格从低到高 */
        PRICE_ASC,
        /** 价格从高到低 */
        PRICE_DESC
    }

    private Double minPrice;
    private Double maxPrice;
    /** 偏好最近 N 天内发布（用于排序加权，不剔除其它商品），null 表示不强调时效 */
    private Integer maxPublishDays;
    private SortBy sortBy = SortBy.BEST_FIT;
    /** 登录用户 ID，用于画像匹配与默认价格区间 */
    private Integer userId;
    /** 未填价格区间时，是否用用户画像中的价格偏好 */
    private boolean useProfilePriceRange = true;

    public Double getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(Double minPrice) {
        this.minPrice = minPrice;
    }

    public Double getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(Double maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Integer getMaxPublishDays() {
        return maxPublishDays;
    }

    public void setMaxPublishDays(Integer maxPublishDays) {
        this.maxPublishDays = maxPublishDays;
    }

    /** 时效下拉选「最新」：按发布日期（到天）降序，同一天按综合推荐分 */
    public boolean isLatestDayPrimarySort() {
        return maxPublishDays != null && maxPublishDays == -1;
    }

    /** 近 N 天加权（7/30/3）；「最新」与「不限」不参与该加权 */
    public Integer getPreferWithinDays() {
        if (maxPublishDays == null || maxPublishDays <= 0 || isLatestDayPrimarySort()) {
            return null;
        }
        return maxPublishDays;
    }

    public SortBy getSortBy() {
        return sortBy != null ? sortBy : SortBy.BEST_FIT;
    }

    public void setSortBy(SortBy sortBy) {
        this.sortBy = sortBy;
    }

    public static SortBy parseSortBy(String raw) {
        if (raw == null || raw.isEmpty()) {
            return SortBy.BEST_FIT;
        }
        try {
            return SortBy.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return SortBy.BEST_FIT;
        }
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public boolean isUseProfilePriceRange() {
        return useProfilePriceRange;
    }

    public void setUseProfilePriceRange(boolean useProfilePriceRange) {
        this.useProfilePriceRange = useProfilePriceRange;
    }

    public boolean hasAnyConstraint() {
        return minPrice != null || maxPrice != null
                || (maxPublishDays != null && maxPublishDays > 0)
                || isLatestDayPrimarySort();
    }
}
