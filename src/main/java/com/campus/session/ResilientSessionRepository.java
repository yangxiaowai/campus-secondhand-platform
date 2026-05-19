package com.campus.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.session.MapSession;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Session 运行期降级：Redis 不可用时回退到进程内内存 Session，避免请求 500。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ResilientSessionRepository implements SessionRepository<Session> {

    private static final Logger log = LoggerFactory.getLogger(ResilientSessionRepository.class);

    private final SessionRepository delegate;
    private final MapSessionRepository fallbackRepository;

    public ResilientSessionRepository(SessionRepository delegate) {
        this.delegate = delegate;
        this.fallbackRepository = new MapSessionRepository(new ConcurrentHashMap<>());
    }

    @Override
    public Session createSession() {
        try {
            return delegate.createSession();
        } catch (Exception e) {
            if (isRedisFailure(e)) {
                log.warn("[Session] Redis 不可用，使用内存 Session: {}", e.getMessage());
                return fallbackRepository.createSession();
            }
            throw e;
        }
    }

    @Override
    public void save(Session session) {
        try {
            delegate.save(session);
        } catch (Exception e) {
            if (isRedisFailure(e)) {
                log.warn("[Session] Redis 保存失败，写入内存 Session: {}", e.getMessage());
                fallbackRepository.save(toMapSession(session));
                return;
            }
            throw e;
        }
    }

    @Override
    public Session findById(String id) {
        try {
            Session session = delegate.findById(id);
            if (session != null) {
                return session;
            }
        } catch (Exception e) {
            if (!isRedisFailure(e)) {
                throw e;
            }
            log.warn("[Session] Redis 读取失败，尝试内存 Session: {}", e.getMessage());
        }
        return fallbackRepository.findById(id);
    }

    @Override
    public void deleteById(String id) {
        try {
            delegate.deleteById(id);
        } catch (Exception e) {
            if (!isRedisFailure(e)) {
                throw e;
            }
            log.warn("[Session] Redis 删除失败: {}", e.getMessage());
        }
        fallbackRepository.deleteById(id);
    }

    private static MapSession toMapSession(Session session) {
        if (session instanceof MapSession) {
            return (MapSession) session;
        }
        return new MapSession(session);
    }

    private static boolean isRedisFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RedisConnectionFailureException
                    || t instanceof DataAccessException
                    || t instanceof JedisConnectionException
                    || t instanceof JedisException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Unexpected end of stream")
                    || msg.contains("Connection reset")
                    || msg.contains("Broken pipe"))) {
                return true;
            }
        }
        return false;
    }
}
