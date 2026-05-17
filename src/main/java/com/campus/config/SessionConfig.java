package com.campus.config;

import com.campus.session.ResilientSessionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 分布式 Session 配置
 * 成员1：使用 Redis 存储 Session，实现多实例间登录状态共享
 * 成员D：启动期 NO_OP + 延迟 MessageListener；运行期 ResilientSessionRepository 降级
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {

    /**
     * 禁止启动时执行 Redis CONFIG NOTIFY，避免 Redis 未启动导致上下文加载失败
     */
    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }

    /**
     * 覆盖 Spring Session 默认监听容器（bean 名必须为 springSessionRedisMessageListenerContainer）
     */
    @Bean(name = "springSessionRedisMessageListenerContainer")
    @Primary
    @Order(0)
    RedisMessageListenerContainer springSessionRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        LazyRedisMessageListenerContainer container = new LazyRedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /**
     * 运行期 Session 降级包装
     */
    @Bean
    @Primary
    public SessionRepository<?> resilientSessionRepository(RedisIndexedSessionRepository redisSessionRepository) {
        return new ResilientSessionRepository(redisSessionRepository);
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSIONID");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(false);
        return serializer;
    }
}
