package com.campus.config;

import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 延迟启动的 Redis 监听容器，避免 Spring 刷新时因 Redis 未就绪导致上下文加载失败。
 */
public class LazyRedisMessageListenerContainer extends RedisMessageListenerContainer {

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    @Override
    public void start() {
        // 不连接 Redis；Session 过期事件监听在 Redis 恢复后可手动 start()
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
