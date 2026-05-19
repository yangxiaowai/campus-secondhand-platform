package com.campus.config;

import com.campus.session.ResilientSessionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * 分布式 Session：Redis 存储 + 运行期内存降级；显式注册 Filter 确保走 resilientSessionRepository。
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {

    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }

    @Bean(name = "springSessionRedisMessageListenerContainer")
    @Primary
    @Order(0)
    RedisMessageListenerContainer springSessionRedisMessageListenerContainer(
            @Qualifier("sessionRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        LazyRedisMessageListenerContainer container = new LazyRedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisIndexedSessionRepository redisIndexedSessionRepository(
            @Qualifier("sessionRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        RedisOperations<Object, Object> sessionRedis =
                (RedisOperations<Object, Object>) (RedisOperations<?, ?>) redisTemplate;
        RedisIndexedSessionRepository repository = new RedisIndexedSessionRepository(sessionRedis);
        repository.setDefaultMaxInactiveInterval(1800);
        return repository;
    }

    @Bean(name = "sessionRepository")
    public SessionRepository<? extends Session> resilientSessionRepository(
            RedisIndexedSessionRepository redisIndexedSessionRepository) {
        return new ResilientSessionRepository(redisIndexedSessionRepository);
    }

    /**
     * 显式绑定降级后的 sessionRepository，避免 Filter 注入到未包装的 RedisIndexedSessionRepository。
     */
    @Bean
    public SessionRepositoryFilter<? extends Session> springSessionRepositoryFilter(
            @Qualifier("sessionRepository") SessionRepository<? extends Session> sessionRepository) {
        return new SessionRepositoryFilter<>(sessionRepository);
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
