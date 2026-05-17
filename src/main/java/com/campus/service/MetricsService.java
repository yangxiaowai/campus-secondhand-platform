package com.campus.service;

import java.util.Map;

/**
 * 成员D：推荐链路指标采集
 */
public interface MetricsService {

    void recordMatch(long durationMs, int pushCount);

    void recordRecommend(long durationMs);

    void recordCacheHit();

    void recordCacheMiss();

    void recordDegradeL1();

    void recordDegradeL2();

    /**
     * 当前指标快照（供监控页与定时日志使用）
     */
    Map<String, Object> snapshot();
}
