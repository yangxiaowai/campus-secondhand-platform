package com.campus.controller;

import com.campus.annotation.Log;
import com.campus.entity.User;
import com.campus.service.UserService;
import com.campus.util.SessionUserHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 用户控制器
 */
@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    // 验证码有效期（5分钟）
    private static final long CAPTCHA_EXPIRE_TIME = 5 * 60 * 1000;

    /**
     * 跳转到登录页
     */
    @RequestMapping("/loginPage")
    public String loginPage() {
        return "user/login";
    }

    /**
     * 跳转到注册页
     */
    @RequestMapping("/registerPage")
    public String registerPage() {
        return "user/register";
    }

    /**
     * 用户注册
     */
    @Log("用户注册")
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> register(User user, String captcha, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        // 验证码校验（可选，注册时也可以不要验证码）
        // 如果需要注册验证码，取消下面的注释
        /*
        if (!validateCaptcha(captcha, session)) {
            result.put("success", false);
            result.put("message", "验证码错误或已过期");
            return result;
        }
        */

        boolean success = userService.register(user);
        result.put("success", success);
        if (!success) {
            result.put("message", "用户名已存在");
        }
        return result;
    }

    /**
     * 用户登录
     * 技术亮点：增加验证码校验，防止暴力破解
     */
    @Log("用户登录")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> login(String username, String password, String captcha, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        // 验证码校验
        if (!validateCaptcha(captcha, session)) {
            result.put("success", false);
            result.put("message", "验证码错误或已过期");
            return result;
        }

        User user = userService.login(username, password);
        if (user != null) {
            session.setAttribute("user", user);
            result.put("success", true);
            result.put("isAdmin", Objects.equals(user.getRole(), 2));
        } else {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
        }
        return result;
    }

    /**
     * 校验验证码
     */
    private boolean validateCaptcha(String inputCaptcha, HttpSession session) {
        if (inputCaptcha == null || inputCaptcha.isEmpty()) {
            return false;
        }

        String normalizedInput = inputCaptcha.trim().toLowerCase();
        long now = System.currentTimeMillis();

        // 优先使用验证码缓存（支持最近几次验证码）
        LinkedHashMap<String, Long> captchaCache = readCaptchaCache(session.getAttribute("captchaCache"));
        if (!captchaCache.isEmpty()) {
            boolean hit = false;

            Iterator<Map.Entry<String, Long>> iterator = captchaCache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                String code = entry.getKey();
                Long createTime = entry.getValue();
                boolean expired = createTime == null || now - createTime > CAPTCHA_EXPIRE_TIME;
                if (expired) {
                    iterator.remove();
                    continue;
                }
                if (!hit && code != null && code.equals(normalizedInput)) {
                    hit = true;
                    // 一次性使用，命中后立即删除
                    iterator.remove();
                }
            }
            session.setAttribute("captchaCache", captchaCache);
            if (hit) {
                session.removeAttribute("captcha");
                session.removeAttribute("captchaTime");
                return true;
            }
        }

        // 兼容旧逻辑（只保留一个验证码）
        Object captchaObj = session.getAttribute("captcha");
        Object captchaTimeObj = session.getAttribute("captchaTime");
        String sessionCaptcha = captchaObj == null ? null : String.valueOf(captchaObj).toLowerCase();
        Long captchaTime = parseCaptchaTime(captchaTimeObj);

        // 验证码不存在
        if (sessionCaptcha == null || sessionCaptcha.isEmpty() || captchaTime == null) {
            return false;
        }

        // 验证码已过期
        if (now - captchaTime > CAPTCHA_EXPIRE_TIME) {
            session.removeAttribute("captcha");
            session.removeAttribute("captchaTime");
            return false;
        }

        // 校验验证码（忽略大小写）
        boolean valid = sessionCaptcha.equals(normalizedInput);

        // 验证成功后清除验证码（一次性使用）
        if (valid) {
            session.removeAttribute("captcha");
            session.removeAttribute("captchaTime");
        }

        return valid;
    }

    private Long parseCaptchaTime(Object captchaTimeObj) {
        if (captchaTimeObj == null) {
            return null;
        }
        if (captchaTimeObj instanceof Number) {
            return ((Number) captchaTimeObj).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(captchaTimeObj));
        } catch (Exception e) {
            return null;
        }
    }

    private LinkedHashMap<String, Long> readCaptchaCache(Object cacheObj) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        if (!(cacheObj instanceof Map)) {
            return result;
        }
        Map<?, ?> rawMap = (Map<?, ?>) cacheObj;
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Number)) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()).toLowerCase(), ((Number) entry.getValue()).longValue());
        }
        return result;
    }

    /**
     * 退出登录
     */
    @Log("退出登录")
    @RequestMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    /**
     * 个人中心
     */
    @RequestMapping("/center")
    public String center() {
        return "user/center";
    }

    /**
     * 更新用户信息
     */
    @Log("更新个人信息")
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> update(User user, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User sessionUser = SessionUserHelper.getLoginUser(session);
        user.setId(sessionUser.getId());
        boolean success = userService.update(user);
        if (success) {
            // 更新session中的用户信息
            User updatedUser = userService.findById(user.getId());
            session.setAttribute("user", updatedUser);
        }
        result.put("success", success);
        return result;
    }
}
