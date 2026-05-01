package com.campus.event;

import com.campus.entity.Product;
import org.springframework.context.ApplicationEvent;

/**
 * 商品发布事件
 */
public class ProductPublishedEvent extends ApplicationEvent {

    private final Product product;

    public ProductPublishedEvent(Object source, Product product) {
        super(source);
        this.product = product;
    }

    public Product getProduct() {
        return product;
    }
}

