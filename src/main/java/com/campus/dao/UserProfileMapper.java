package com.campus.dao;

import com.campus.entity.UserProfile;
import org.apache.ibatis.annotations.Param;

/**
 * 用户画像 Mapper 接口
 * 
 * 成员A：用户兴趣画像系统
 * 
 * 功能：提供用户画像数据的 MySQL 持久化操作。
 * 画像数据以 JSON 格式存储在 t_user_profile 表中，
 * 主要用于：
 *   1. 重启后从 MySQL 恢复 Redis 缓存
 *   2. 定时任务全量重建画像时写入
 *   3. 作为 Redis 缓存丢失后的持久化备份
 * 
 * 注意：日常的画像读写走 Redis（高性能），
 * MySQL 只作为持久化备份和全量计算的存储层。
 */
public interface UserProfileMapper {

    /**
     * 插入或更新用户画像（ON DUPLICATE KEY UPDATE）
     * 如果用户已存在则更新，不存在则插入
     */
    int upsert(UserProfile profile);

    /**
     * 根据用户ID查询画像
     */
    UserProfile findByUserId(@Param("userId") Integer userId);

    /**
     * 删除用户画像
     */
    int deleteByUserId(@Param("userId") Integer userId);

    /**
     * 查询所有有画像数据的用户ID列表
     * 用于定时任务全量重建
     */
    java.util.List<Integer> findAllUserIds();
}
