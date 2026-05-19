package com.campus.service;

import java.util.List;
import java.util.Map;

/**
 * 成员4：推荐收件箱（Redis ZSet，score=匹配度，按降序读取）
 */
public interface InboxService {

    /**
     * 向用户收件箱推送商品（按匹配度作为 score）
     */
    void push(Integer userId, Integer productId, double matchScore);

    /**
     * 读取收件箱，按匹配度从高到低
     */
    List<Map<String, Object>> listSorted(Integer userId, int limit);
}
