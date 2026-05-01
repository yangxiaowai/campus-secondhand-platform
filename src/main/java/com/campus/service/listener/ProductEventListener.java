package com.campus.service.listener;

import com.campus.entity.Product;
import com.campus.event.ProductPublishedEvent;
import com.campus.service.ProductMatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 商品事件监听器
 */
@Component
public class ProductEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ProductEventListener.class);

    @Autowired
    private ProductMatchService productMatchService;

    @EventListener
    public void handleProductPublished(ProductPublishedEvent event) {
        Product product = event.getProduct();
        if (product == null) {
            logger.info("[商品发布事件] 商品已发布，但商品信息为空");
            return;
        }
        logger.info("[商品发布事件] 商品《{}》已发布，ID={}", product.getName(), product.getId());
        productMatchService.processPublishedProduct(product);
    }
}

