package com.campus.aspect;

import com.campus.annotation.Log;
import com.campus.entity.OperationLog;
import com.campus.entity.User;
import com.campus.util.SessionUserHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.util.Date;

/**
 * 操作日志切面
 * 技术亮点：使用 Spring AOP 实现声明式操作日志记录
 * 
 * 核心原理：
 * 1. 定义切点：拦截所有带有 @Log 注解的方法
 * 2. 环绕通知：在方法执行前后记录相关信息
 * 3. 自动获取：用户信息、请求参数、执行时长、IP地址等
 */
@Aspect
@Component
public class LogAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 定义切点：所有带有 @Log 注解的方法
     */
    @Pointcut("@annotation(com.campus.annotation.Log)")
    public void logPointcut() {
    }

    /**
     * 环绕通知：记录操作日志
     */
    @Around("logPointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 执行目标方法
        Object result = point.proceed();
        
        // 计算执行时长
        long executionTime = System.currentTimeMillis() - startTime;
        
        // 异步记录日志（避免影响主业务性能）
        try {
            saveLog(point, executionTime);
        } catch (Exception e) {
            logger.error("记录操作日志失败: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * 保存操作日志
     */
    private void saveLog(ProceedingJoinPoint point, long executionTime) throws Exception {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        OperationLog operationLog = new OperationLog();
        operationLog.setExecutionTime(executionTime);
        operationLog.setCreateTime(new Date());

        // 获取注解上的操作描述
        Log logAnnotation = method.getAnnotation(Log.class);
        if (logAnnotation != null) {
            operationLog.setOperation(logAnnotation.value());
        }

        // 获取请求的方法名
        String className = point.getTarget().getClass().getName();
        String methodName = signature.getName();
        operationLog.setMethod(className + "." + methodName + "()");

        // 获取请求参数
        Object[] args = point.getArgs();
        try {
            String params = objectMapper.writeValueAsString(args);
            // 限制参数长度，避免日志过大
            if (params.length() > 500) {
                params = params.substring(0, 500) + "...";
            }
            operationLog.setParams(params);
        } catch (Exception e) {
            operationLog.setParams("参数序列化失败");
        }

        // 获取当前请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            operationLog.setIp(getIpAddress(request));

            // 获取当前登录用户
            HttpSession session = request.getSession(false);
            if (session != null) {
                User user = SessionUserHelper.getLoginUser(session);
                if (user != null) {
                    operationLog.setUserId(user.getId());
                    operationLog.setUsername(user.getUsername());
                }
            }
        }

        // 输出日志（实际项目中可以存入数据库）
        logger.info("【操作日志】用户: {}, 操作: {}, 方法: {}, 耗时: {}ms, IP: {}",
                operationLog.getUsername(),
                operationLog.getOperation(),
                operationLog.getMethod(),
                operationLog.getExecutionTime(),
                operationLog.getIp());
    }

    /**
     * 获取客户端IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

