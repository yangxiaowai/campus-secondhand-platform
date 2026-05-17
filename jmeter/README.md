# 成员E - 压测脚本目录

## 目录结构

```
jmeter/
├── recommend-benchmark.jmx    # JMeter 主测试计划
├── README.md                  # 本文件
└── results/                   # 压测结果输出目录
```

## 测试场景

| 场景 | 并发数 | 循环次数 | 目的 |
|------|--------|----------|------|
| 场景1 - 基准测试 | 1 | 50 | 获取单用户响应基准 |
| 场景2 - 并发测试 | 10 | 20 | 模拟常规并发负载 |
| 场景3 - 压力测试 | 50 | 10 | 压力测试极限性能 |
| 场景4 - 降级测试 | 10 | 20 | 验证Redis断开后表现 |

## 使用方法

### 方式一：JMeter GUI 打开
1. 安装 JMeter：https://jmeter.apache.org/download_jmeter.cgi
2. 解压后运行 `bin/jmeter.bat`（Windows）
3. 打开 `recommend-benchmark.jmx` 文件
4. 点击运行按钮开始压测

### 方式二：命令行运行
```bash
# 基准测试
jmeter -n -t recommend-benchmark.jmx -l results/result_baseline.jtl -j results/jmeter_baseline.log

# 并发测试
jmeter -n -t recommend-benchmark.jmx -l results/result_concurrent.jtl -j results/jmeter_concurrent.log

# 压力测试
jmeter -n -t recommend-benchmark.jmx -l results/result_stress.jtl -j results/jmeter_stress.log

# 降级测试（需先停止Redis）
jmeter -n -t recommend-benchmark.jmx -l results/result_degrade.jtl -j results/jmeter_degrade.log
```

### 查看结果
```bash
# 生成HTML报告
jmeter -g results/result_baseline.jtl -o results/report_baseline
```

## 测试接口

- 推荐接口：`GET /product/recommendations`
- 健康检查：`GET /admin/health`