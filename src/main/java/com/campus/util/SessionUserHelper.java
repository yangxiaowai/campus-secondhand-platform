package com.campus.util;

import com.campus.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * 从 Session 安全读取登录用户。
 * Spring Session + Jackson 反序列化时，User 可能变成 LinkedHashMap，直接强转会 ClassCastException。
 */
public final class SessionUserHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SessionUserHelper() {
    }

    public static User getLoginUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute("user");
        if (attr == null) {
            return null;
        }
        if (attr instanceof User) {
            return (User) attr;
        }
        if (attr instanceof Map) {
            try {
                return MAPPER.convertValue(attr, User.class);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    public static boolean isLoggedIn(HttpSession session) {
        return getLoginUser(session) != null;
    }
}
