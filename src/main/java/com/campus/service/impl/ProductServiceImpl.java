package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.entity.Product;
import com.campus.service.MatchEngine;
import com.campus.service.ProductSearchIndexService;
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

    @Autowired(required = false)
    private ProductSearchIndexService productSearchIndexService;

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
        boolean ok = productMapper.update(product) > 0;
        if (ok && productSearchIndexService != null && product.getId() != null) {
            try {
                Product persisted = productMapper.findById(product.getId());
                if (persisted != null) {
                    productSearchIndexService.indexProduct(persisted);
                }
            } catch (Exception ex) {
                logger.warn("搜索索引更新失败 productId={}", product.getId(), ex);
            }
        }
        return ok;
    }

    @Override
    public boolean delete(Integer id) {
        try {
            boolean ok = productMapper.delete(id) > 0;
            if (ok && productSearchIndexService != null) {
                productSearchIndexService.removeProduct(id);
            }
            return ok;
        } catch (Exception ex) {
            logger.warn("商品物理删除失败，降级为逻辑删除，productId={}", id, ex);
            boolean ok = productMapper.updateStatus(id, STATUS_DELETED) > 0;
            if (ok && productSearchIndexService != null) {
                productSearchIndexService.removeProduct(id);
            }
            return ok;
        }
    }

    @Override
    public void increaseViewCount(Integer id) {
        productMapper.increaseViewCount(id);
    }

    @Override
    public boolean updateStatus(Integer id, Integer status) {
        boolean ok = productMapper.updateStatus(id, status) > 0;
        if (ok && productSearchIndexService != null) {
            try {
                if (status != null && status == 0) {
                    Product p = productMapper.findById(id);
                    if (p != null) {
                        productSearchIndexService.indexProduct(p);
                    }
                } else {
                    productSearchIndexService.removeProduct(id);
                }
            } catch (Exception ex) {
                logger.warn("搜索索引状态同步失败 productId={}", id, ex);
            }
        }
        return ok;
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

