package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.event.ProductPublishedEvent;
import com.campus.service.ProductService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品服务实现类
 */
@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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
        boolean success = productMapper.insert(product) > 0;
        if (success) {
            eventPublisher.publishEvent(new ProductPublishedEvent(this, product));
        }
        return success;
    }

    @Override
    public boolean update(Product product) {
        return productMapper.update(product) > 0;
    }

    @Override
    public boolean delete(Integer id) {
        return productMapper.delete(id) > 0;
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

