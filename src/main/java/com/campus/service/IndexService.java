package com.campus.service;

import java.util.List;
import java.util.Set;

/**
 * 成员4：两级索引（分类-用户、关键词-用户）
 */
public interface IndexService {

    /**
     * 根据用户画像更新 Redis 索引（画像变更后调用）
     */
    void rebuildUserIndex(Integer userId);

    /**
     * 全量重建所有用户索引（定时任务或画像全量重建后调用）
     */
    void rebuildAllIndexes();

    /**
     * 按商品分类与关键词从索引筛选候选用户：U1 ∩ (U2 ∪ U3 …)
     *
     * @param categoryId     商品分类 ID
     * @param keywords       商品关键词
     * @param totalUserCount 系统用户总数（用于日志对比）
     * @return 候选用户 ID；若索引无命中则返回 null，表示需全量遍历
     */
    Set<Integer> findCandidateUserIds(Integer categoryId, List<String> keywords, int totalUserCount);
}
