package com.campus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 成员D：启用定时任务（指标每 10 秒输出）
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {
}
