package com.campus.service;

import com.campus.entity.Product;

/**
 * 成员B：商品发布事件 → 特征提取 → 匹配用户 → 写入推荐收件箱（Redis ZSet）
 */
public interface MatchEngine {

    /**
     * 商品成功入库后调用：打印分词、写商品特征、为候选用户计算匹配度并写入收件箱。
     *
     * @param product 已持久化且含主键的商品
     */
    void onProductPublished(Product product);
}
