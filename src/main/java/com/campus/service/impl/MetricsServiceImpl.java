package com.campus.service.impl;

import com.campus.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 成员D：指标采集与定时输出
 */
@Service
public class MetricsServiceImpl implements MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsServiceImpl.class);

    private final AtomicLong matchCount = new AtomicLong();
    private final AtomicLong matchTotalMs = new AtomicLong();
    private final AtomicLong pushTotal = new AtomicLong();
    private final AtomicLong recommendCount = new AtomicLong();
    private final AtomicLong recommendTotalMs = new AtomicLong();
    private final AtomicLong cacheHit = new AtomicLong();
    private final AtomicLong cacheMiss = new AtomicLong();
    private final AtomicLong degradeL1 = new AtomicLong();
    private final AtomicLong degradeL2 = new AtomicLong();

    @Override
    public void recordMatch(long durationMs, int pushCount) {
        matchCount.incrementAndGet();
        matchTotalMs.addAndGet(Math.max(0, durationMs));
        pushTotal.addAndGet(Math.max(0, pushCount));
    }

    @Override
    public void recordRecommend(long durationMs) {
        recommendCount.incrementAndGet();
        recommendTotalMs.addAndGet(Math.max(0, durationMs));
    }

    @Override
    public void recordCacheHit() {
        cacheHit.incrementAndGet();
    }

    @Override
    public void recordCacheMiss() {
        cacheMiss.incrementAndGet();
    }

    @Override
    public void recordDegradeL1() {
        degradeL1.incrementAndGet();
    }

    @Override
    public void recordDegradeL2() {
        degradeL2.incrementAndGet();
    }

    @Override
    public Map<String, Object> snapshot() {
        long mCount = matchCount.get();
        long rCount = recommendCount.get();
        long hits = cacheHit.get();
        long misses = cacheMiss.get();
        long cacheTotal = hits + misses;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("matchCount", mCount);
        map.put("matchAvgMs", mCount == 0 ? 0 : matchTotalMs.get() / mCount);
        map.put("pushTotal", pushTotal.get());
        map.put("recommendCount", rCount);
        map.put("recommendAvgMs", rCount == 0 ? 0 : recommendTotalMs.get() / rCount);
        map.put("cacheHit", hits);
        map.put("cacheMiss", misses);
        map.put("cacheHitRate", cacheTotal == 0 ? 0.0 : (hits * 1.0 / cacheTotal));
        map.put("degradeL1", degradeL1.get());
        map.put("degradeL2", degradeL2.get());
        map.put("timestamp", System.currentTimeMillis());
        return map;
    }

    /**
     * 验收：控制台每 10 秒打印一次指标
     */
    @Scheduled(fixedRate = 10000)
    public void printMetrics() {
        Map<String, Object> s = snapshot();
        log.info("[成员D-Metrics] matchCount={}, matchAvgMs={}, pushTotal={}, recommendCount={}, recommendAvgMs={}, " +
                        "cacheHitRate={}, degradeL1={}, degradeL2={}",
                s.get("matchCount"), s.get("matchAvgMs"), s.get("pushTotal"),
                s.get("recommendCount"), s.get("recommendAvgMs"), s.get("cacheHitRate"),
                s.get("degradeL1"), s.get("degradeL2"));
    }
}
