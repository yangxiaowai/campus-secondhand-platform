package com.campus.controller;

import com.campus.service.MetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 成员D：监控看板接口
 */
@Controller
public class MonitorController {

    @Autowired
    private MetricsService metricsService;

    @GetMapping("/monitor.html")
    public String monitorPage() {
        return "forward:/static/monitor.html";
    }

    @GetMapping("/monitor/metrics")
    @ResponseBody
    public Map<String, Object> metrics() {
        return metricsService.snapshot();
    }
}
