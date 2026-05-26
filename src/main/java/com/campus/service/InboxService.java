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
     * 读取收件箱，按匹配度从高到低（含 read 字段）
     */
    List<Map<String, Object>> listSorted(Integer userId, int limit);

    /** 未读条数（收件箱 ZSet 中未标记已读的商品数） */
    int getUnreadCount(Integer userId);

    /** 是否开启免打扰（仍写入收件箱，但不推送实时提醒） */
    boolean isDoNotDisturb(Integer userId);

    /** 设置免打扰 */
    void setDoNotDisturb(Integer userId, boolean enabled);

    /** 一键已读：将当前收件箱内全部商品标为已读 */
    void markAllRead(Integer userId);

    /** 单条标为已读 */
    void markRead(Integer userId, Integer productId);

    /** 是否已读 */
    boolean isRead(Integer userId, Integer productId);

    /**
     * 轮询新通知（自 since 毫秒以来的推送，用于前端实时提醒）
     */
    List<Map<String, Object>> pollNotifications(Integer userId, long since);
}
