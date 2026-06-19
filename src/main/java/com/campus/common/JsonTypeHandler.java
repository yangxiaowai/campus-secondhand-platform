package com.campus.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * MyBatis JSON 类型处理器
 * 
 * 用于处理 MySQL JSON 类型字段与 Java Map 之间的相互转换。
 * 在 UserProfileMapper.xml 中使用，将 t_user_profile.profile_data 字段
 * 的 JSON 字符串自动转换为 Java Map 对象。
 * 
 * 使用方式：
 *   <result property="categoryWeights" column="profile_data"
 *          typeHandler="com.campus.common.JsonTypeHandler"
 *          javaType="java.util.Map"/>
 */
@MappedTypes({Map.class})
@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.OTHER})
public class JsonTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(JsonTypeHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = objectMapper.writeValueAsString(parameter);
            ps.setString(i, json);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            ps.setString(i, "{}");
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, HashMap.class);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: {}", json, e);
            return new HashMap<>();
        }
    }
}
