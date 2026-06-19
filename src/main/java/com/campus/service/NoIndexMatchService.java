package com.campus.service;

import java.util.Map;

/**
 * 无索引推荐服务（暴力匹配）
 * 
 * 不使用 Redis 两级索引，直接遍历所有用户 × 所有商品，
 * 逐个计算匹配度并写入收件箱。用于与有索引方案做性能对比。
 */
public interface NoIndexMatchService {

    /**
     * 遍历所有用户和所有商品，计算匹配度并写入收件箱。
     *
     * @param minScore 最低匹配度阈值，低于此值不写入收件箱
     * @return 统计信息：耗时、用户数、商品数、总计算次数、写入收件箱次数
     */
    Map<String, Object> matchAll(double minScore);
}