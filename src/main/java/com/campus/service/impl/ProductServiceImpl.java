package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.service.MatchEngine;
import com.campus.service.ProductService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品服务实现类
 */
@Service
public class ProductServiceImpl implements ProductService {
    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);
    private static final int STATUS_DELETED = 3;

    @Autowired
    private ProductMapper productMapper;

    @Autowired(required = false)
    private MatchEngine matchEngine;

    @Override
    public PageInfo<Product> findList(String keyword, Integer categoryId, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Product> list = productMapper.findList(keyword, categoryId, 0); // 只查询在售商品
        return new PageInfo<>(list);
    }

    @Override
    public PageInfo<Product> findListWithStatus(String keyword, Integer categoryId, Integer status, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Product> list = productMapper.findList(keyword, categoryId, status);
        return new PageInfo<>(list);
    }

    @Override
    public Product findById(Integer id) {
        return productMapper.findById(id);
    }

    @Override
    public boolean publish(Product product) {
        product.setStatus(0); // 在售状态
        product.setViewCount(0);
        boolean ok = productMapper.insert(product) > 0;
        if (ok && matchEngine != null && product.getId() != null) {
            try {
                Product persisted = productMapper.findById(product.getId());
                matchEngine.onProductPublished(persisted != null ? persisted : product);
            } catch (Exception ex) {
                logger.warn("成员B-MatchEngine 处理发布事件失败 productId={}", product.getId(), ex);
            }
        }
        return ok;
    }

    @Override
    public boolean update(Product product) {
        return productMapper.update(product) > 0;
    }

    @Override
    public boolean delete(Integer id) {
        try {
            // 优先物理删除（无订单引用时）
            return productMapper.delete(id) > 0;
        } catch (Exception ex) {
            // 若被外键拦截（已有订单），自动逻辑删除：标记为已删除
            logger.warn("商品物理删除失败，降级为逻辑删除，productId={}", id, ex);
            return productMapper.updateStatus(id, STATUS_DELETED) > 0;
        }
    }

    @Override
    public void increaseViewCount(Integer id) {
        productMapper.increaseViewCount(id);
    }

    @Override
    public boolean updateStatus(Integer id, Integer status) {
        return productMapper.updateStatus(id, status) > 0;
    }

    @Override
    public List<Product> findByUserId(Integer userId) {
        return productMapper.findByUserId(userId);
    }

    @Override
    public List<Product> findHotProducts(Integer limit) {
        return productMapper.findHotProducts(limit);
    }
}

