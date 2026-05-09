package com.campus.config;

import com.campus.service.RecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class InitTestData implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(InitTestData.class);

    @Autowired(required = false)
    private RecommendService recommendService;

    private boolean initialized = false;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (initialized) return;
        initialized = true;

        if (recommendService == null) {
            logger.warn("RecommendService 未注入，跳过测试数据初始化");
            return;
        }

        try {
            recommendService.initTestData();
            logger.info("推荐收件箱测试数据初始化完成");
        } catch (Exception e) {
            logger.warn("测试数据初始化失败（Redis可能未启动）: {}", e.getMessage());
        }
    }
}
