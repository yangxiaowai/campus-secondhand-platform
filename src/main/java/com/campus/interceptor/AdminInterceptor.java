package com.campus.interceptor;

import com.campus.entity.User;
import com.campus.util.SessionUserHelper;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 管理员拦截器
 */
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        User user = SessionUserHelper.getLoginUser(session);

        if (user == null || user.getRole() != 2) {
            // 不是管理员，跳转到首页
            response.sendRedirect(request.getContextPath() + "/");
            return false;
        }

        return true;
    }
}



