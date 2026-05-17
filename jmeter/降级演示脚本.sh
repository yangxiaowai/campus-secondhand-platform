#!/bin/bash
# ============================================================================
# 校园二手交易平台 - 降级演示脚本
# ============================================================================
# 功能：自动化演示 Redis 断开后系统的降级容错能力
# 使用：./降级演示脚本.sh
# ============================================================================

set -e

APP_URL="http://localhost:8080"
REDIS_CONTAINER="campus-secondhand-redis-1"
RESULT_DIR="jmeter/results"
DEGRADE_RESULT="$RESULT_DIR/result_degrade_jmeter.jtl"

echo "=============================================="
echo "  校园二手交易平台 - 降级容错演示"
echo "=============================================="
echo ""

# 创建结果目录
mkdir -p "$RESULT_DIR"

# 步骤1：检查服务状态
echo "[步骤1] 检查服务状态..."
echo "-------------------------------------------"

# 检查应用是否运行
if curl -s -o /dev/null -w "%{http_code}" "$APP_URL/" | grep -q "200"; then
    echo "✓ 应用服务运行正常 (8080)"
else
    echo "✗ 应用服务未运行，请先执行: docker compose up -d"
    exit 1
fi

# 检查 Redis 是否运行
if docker ps --format "{{.Names}}" | grep -q "$REDIS_CONTAINER"; then
    echo "✓ Redis 服务运行正常"
    REDIS_RUNNING=true
else
    echo "✗ Redis 服务未运行"
    REDIS_RUNNING=false
fi

echo ""

# 步骤2：正常模式测试（Redis 开启）
echo "[步骤2] 正常模式压测（Redis 开启）..."
echo "-------------------------------------------"
echo "执行: 10并发 × 20次 请求"
echo ""

if command -v jmeter &> /dev/null; then
    jmeter -n -t jmeter/recommend-benchmark.jmx -l "$RESULT_DIR/result_normal.jtl" -j "$RESULT_DIR/jmeter_normal.log" 2>/dev/null || true
    echo "✓ 正常模式压测完成"
else
    echo "! JMeter 未安装，跳过命令行压测"
    echo "  请在 JMeter GUI 中手动执行场景1和场景2"
fi

echo ""

# 步骤3：停止 Redis，触发降级
echo "[步骤3] 停止 Redis，触发降级..."
echo "-------------------------------------------"
echo "执行: docker compose stop redis"
echo ""

docker compose stop redis
echo "✓ Redis 已停止"
sleep 2

echo ""
echo "! Redis 已停止，系统正在降级..."
echo "! 推荐接口现在应返回 MySQL 备选方案（热门商品）"
echo ""

# 步骤4：降级模式测试
echo "[步骤4] 降级模式压测（Redis 断开）..."
echo "-------------------------------------------"
echo "执行: 10并发 × 20次 请求"
echo ""

if command -v jmeter &> /dev/null; then
    jmeter -n -t jmeter/recommend-benchmark.jmx -l "$RESULT_DIR/result_degrade.jtl" -j "$RESULT_DIR/jmeter_degrade.log" 2>/dev/null || true
    echo "✓ 降级模式压测完成"
else
    echo "! JMeter 未安装，跳过命令行压测"
    echo "  请手动测试推荐接口，观察返回结果"
fi

echo ""

# 步骤5：验证降级效果
echo "[步骤5] 验证降级效果..."
echo "-------------------------------------------"

# 测试推荐接口
RESPONSE=$(curl -s -w "\n%{http_code}" "$APP_URL/product/recommendations?userId=1")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "200" ]; then
    echo "✓ 降级成功！推荐接口返回 200"
    echo "  系统自动切换到 MySQL 备选方案"
else
    echo "✗ 降级失败！推荐接口返回 $HTTP_CODE"
fi

echo ""
echo "推荐接口响应内容（前500字符）："
echo "$BODY" | head -c 500
echo ""
echo ""

# 步骤6：重启 Redis
echo "[步骤6] 重启 Redis，恢复正常模式..."
echo "-------------------------------------------"
echo "执行: docker compose start redis"
echo ""

docker compose start redis
echo "✓ Redis 已重启"
sleep 3

# 等待 Redis 连接恢复
echo "等待 Redis 连接恢复..."
for i in {1..10}; do
    if docker exec "$REDIS_CONTAINER" redis-cli ping 2>/dev/null | grep -q "PONG"; then
        echo "✓ Redis 连接恢复"
        break
    fi
    sleep 1
done

echo ""

# 步骤7：恢复正常模式测试
echo "[步骤7] 恢复正常模式，验证服务恢复..."
echo "-------------------------------------------"

NORMAL_RESPONSE=$(curl -s -w "\n%{http_code}" "$APP_URL/product/recommendations?userId=1")
NORMAL_CODE=$(echo "$NORMAL_RESPONSE" | tail -n1)

if [ "$NORMAL_CODE" = "200" ]; then
    echo "✓ 恢复正常！推荐接口返回 200"
else
    echo "✗ 恢复失败！推荐接口返回 $NORMAL_CODE"
fi

echo ""
echo "=============================================="
echo "  降级演示完成"
echo "=============================================="
echo ""
echo "结果文件："
echo "  - 正常模式: $RESULT_DIR/result_normal.jtl"
echo "  - 降级模式: $RESULT_DIR/result_degrade.jtl"
echo ""
echo "对比两次压测结果，验证降级效果"
echo ""
echo "=============================================="