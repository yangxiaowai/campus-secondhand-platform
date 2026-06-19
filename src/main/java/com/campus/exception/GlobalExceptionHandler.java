package com.campus.exception;

import com.campus.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public Result<Object> handleBusinessException(BusinessException e, HttpServletRequest request) {
        logger.warn("业务异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NullPointerException.class)
    public Object handleNullPointerException(NullPointerException e, HttpServletRequest request,
                                             RedirectAttributes redirectAttributes) {
        logger.error("空指针异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return pageOrJsonError(request, redirectAttributes, "页面数据异常，请稍后重试");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request,
                                                 RedirectAttributes redirectAttributes) {
        logger.warn("参数异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        if (wantsJsonResponse(request)) {
            return Result.error(400, e.getMessage());
        }
        redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        return "redirect:/product/list";
    }

    @ExceptionHandler({RedisConnectionFailureException.class, DataAccessException.class})
    @ResponseBody
    public Result<Object> handleRedisException(Exception e, HttpServletRequest request) {
        logger.warn("Redis 异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error(503, "Redis 暂不可用，推荐服务已降级，请稍后重试或使用 /test/degrade/recommend 验收");
    }

    @ExceptionHandler(ClassCastException.class)
    public Object handleClassCastException(ClassCastException e, HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        logger.error("类型转换异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        if (e.getMessage() != null && e.getMessage().contains("RedisSession")) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "登录状态已失效，请清除浏览器 Cookie 后重新登录");
            return "redirect:/user/loginPage";
        }
        return pageOrJsonError(request, redirectAttributes, "系统繁忙，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        logger.error("系统异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return pageOrJsonError(request, redirectAttributes, "系统繁忙，请稍后重试");
    }

    private static Object pageOrJsonError(HttpServletRequest request, RedirectAttributes redirectAttributes,
                                          String message) {
        if (wantsJsonResponse(request)) {
            return Result.error(message);
        }
        redirectAttributes.addFlashAttribute("errorMsg", message);
        return "redirect:/product/list";
    }

    private static boolean wantsJsonResponse(HttpServletRequest request) {
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && (uri.contains("/test/")
                || uri.contains("/user/profile")
                || uri.contains("/monitor/metrics")
                || uri.contains("/product/recommendations")
                || uri.endsWith("/user/login")
                || uri.endsWith("/user/register")
                || uri.endsWith("/user/update")
                || uri.contains("/product/publish") && "POST".equalsIgnoreCase(request.getMethod())
                || uri.contains("/order/"));
    }
}
