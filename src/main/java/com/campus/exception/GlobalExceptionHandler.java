package com.campus.exception;

import com.campus.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器
 * 技术亮点：使用 @ControllerAdvice + @ExceptionHandler 实现全局异常统一处理
 * 避免在每个 Controller 中重复编写 try-catch，提高代码复用性和可维护性
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public Result<Object> handleBusinessException(BusinessException e, HttpServletRequest request) {
        logger.warn("业务异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseBody
    public Result<Object> handleNullPointerException(NullPointerException e, HttpServletRequest request) {
        logger.error("空指针异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error("系统内部错误，请稍后重试");
    }

    /**
     * 处理参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public Result<Object> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        logger.warn("参数异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * Redis 连接失败（推荐降级场景应在上层捕获，此处作兜底提示）
     */
    @ExceptionHandler({RedisConnectionFailureException.class, DataAccessException.class})
    @ResponseBody
    public Result<Object> handleRedisException(Exception e, HttpServletRequest request) {
        logger.warn("Redis 异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error(503, "Redis 暂不可用，推荐服务已降级，请稍后重试或使用 /test/degrade/recommend 验收");
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result<Object> handleException(Exception e, HttpServletRequest request) {
        logger.error("系统异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error("系统繁忙，请稍后重试");
    }
}

