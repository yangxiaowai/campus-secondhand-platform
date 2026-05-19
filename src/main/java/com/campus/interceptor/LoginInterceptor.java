package com.campus.interceptor;

import com.campus.util.SessionUserHelper;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;

/**
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        if (!SessionUserHelper.isLoggedIn(session)) {
            // Ajax 请求返回 JSON，避免前端把登录页 HTML 当成接口结果
            String xRequestedWith = request.getHeader("X-Requested-With");
            if ("XMLHttpRequest".equalsIgnoreCase(xRequestedWith)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json;charset=UTF-8");
                PrintWriter writer = response.getWriter();
                writer.write("{\"success\":false,\"message\":\"登录已失效，请重新登录\"}");
                writer.flush();
                return false;
            }
            // 普通请求跳转到登录页
            response.sendRedirect(request.getContextPath() + "/user/loginPage");
            return false;
        }

        return true;
    }
}



