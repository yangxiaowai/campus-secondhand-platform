package com.campus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * Redis 配置：业务与 Session 使用独立连接池，避免压测时互相抢连接导致 500。
 */
@Configuration
@PropertySource("classpath:redis-config.properties")
public class RedisConfig {

    @Value("${redis.host}")
    private String host;

    @Value("${redis.port}")
    private int port;

    @Value("${redis.password}")
    private String password;

    @Value("${redis.database}")
    private int database;

    @Value("${redis.timeout}")
    private int timeout;

    @Value("${redis.pool.maxTotal:600}")
    private int poolMaxTotal;

    @Value("${redis.pool.maxIdle:150}")
    private int poolMaxIdle;

    @Value("${redis.pool.minIdle:50}")
    private int poolMinIdle;

    @Value("${redis.pool.maxWaitMillis:30000}")
    private long poolMaxWaitMillis;

    @Value("${redis.session.pool.maxTotal:400}")
    private int sessionPoolMaxTotal;

    @Value("${redis.session.pool.maxIdle:150}")
    private int sessionPoolMaxIdle;

    @Value("${redis.session.pool.minIdle:80}")
    private int sessionPoolMinIdle;

    @Value("${redis.session.pool.maxWaitMillis:30000}")
    private long sessionPoolMaxWaitMillis;

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return createFactory(poolMaxTotal, poolMaxIdle, poolMinIdle, poolMaxWaitMillis);
    }

    @Bean(name = "sessionRedisConnectionFactory")
    public RedisConnectionFactory sessionRedisConnectionFactory() {
        return createFactory(sessionPoolMaxTotal, sessionPoolMaxIdle, sessionPoolMinIdle, sessionPoolMaxWaitMillis);
    }

    @Bean(name = "sessionRedisTemplate")
    public RedisTemplate<String, Object> sessionRedisTemplate(
            RedisConnectionFactory sessionRedisConnectionFactory) {
        return buildRedisTemplate(sessionRedisConnectionFactory);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return buildRedisTemplate(redisConnectionFactory);
    }

    private JedisConnectionFactory createFactory(int maxTotal, int maxIdle, int minIdle, long maxWaitMillis) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWaitMillis(maxWaitMillis);
        poolConfig.setBlockWhenExhausted(true);
        // fairness=true 在高并发下公平锁会放大等待，压测更易池耗尽
        poolConfig.setFairness(false);
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRunsMillis(30_000);
        poolConfig.setMinEvictableIdleTimeMillis(60_000);
        poolConfig.setNumTestsPerEvictionRun(3);

        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
                .usePooling()
                .poolConfig(poolConfig)
                .and()
                .readTimeout(Duration.ofMillis(timeout))
                .connectTimeout(Duration.ofMillis(timeout))
                .build();

        JedisConnectionFactory factory = new JedisConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    private static RedisTemplate<String, Object> buildRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        Jackson2JsonRedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jacksonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jacksonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
