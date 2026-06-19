package com.campus.dao;

import com.campus.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品Mapper接口
 */
public interface ProductMapper {
    /**
     * 分页查询商品列表
     */
    List<Product> findList(@Param("keyword") String keyword, 
                           @Param("categoryId") Integer categoryId,
                           @Param("status") Integer status);

    /**
     * 根据ID查询商品详情
     */
    Product findById(@Param("id") Integer id);

    /**
     * 按 ID 列表批量查询（搜索 Hydrate）
     */
    List<Product> findByIds(@Param("ids") List<Integer> ids);

    /**
     * 插入商品
     */
    int insert(Product product);

    /**
     * 更新商品
     */
    int update(Product product);

    /**
     * 删除商品
     */
    int delete(@Param("id") Integer id);

    /**
     * 增加浏览量
     */
    int increaseViewCount(@Param("id") Integer id);

    /**
     * 更新商品状态
     */
    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);

    /**
     * 查询用户发布的商品
     */
    List<Product> findByUserId(@Param("userId") Integer userId);

    /**
     * 查询热门商品（按浏览量排序）
     */
    List<Product> findHotProducts(@Param("limit") Integer limit);

    /**
     * 统计商品总数
     */
    int count(@Param("keyword") String keyword, 
              @Param("categoryId") Integer categoryId,
              @Param("status") Integer status);
}



