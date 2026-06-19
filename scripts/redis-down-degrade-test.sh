#!/usr/bin/env bash
# Redis 关闭后降级验收（勿重启 Tomcat）
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
REDIS_PORT="${REDIS_PORT:-6379}"

check_http() {
  local name="$1"
  local url="$2"
  local expect="$3"
  local code body
  body=$(curl -sf -m 15 -w "\n%{http_code}" "$url" 2>/dev/null || echo -e "\n000")
  code=$(echo "$body" | tail -1)
  body=$(echo "$body" | sed '$d')
  if echo "$body" | grep -q "$expect" && [[ "$code" == "200" ]]; then
    echo "[PASS] $name (HTTP $code)"
    return 0
  fi
  echo "[FAIL] $name (HTTP $code) expect contains: $expect"
  echo "       body: $(echo "$body" | head -c 200)"
  return 1
}

stop_redis() {
  if redis-cli -p "$REDIS_PORT" PING >/dev/null 2>&1; then
    echo ">>> 停止 Redis (port $REDIS_PORT)..."
    if command -v brew >/dev/null 2>&1; then
      brew services stop redis 2>/dev/null || true
    fi
    redis-cli -p "$REDIS_PORT" SHUTDOWN NOSAVE >/dev/null 2>&1 || true
    sleep 2
  fi
  if redis-cli -p "$REDIS_PORT" PING >/dev/null 2>&1; then
    echo "Redis 仍在运行，请手动: brew services stop redis"
    exit 1
  fi
  echo ">>> Redis 已停止"
}

start_redis() {
  if redis-cli -p "$REDIS_PORT" PING >/dev/null 2>&1; then
    return 0
  fi
  echo ">>> 尝试启动 Redis..."
  if command -v brew >/dev/null 2>&1; then
    brew services start redis 2>/dev/null || true
  fi
  for _ in $(seq 1 15); do
    redis-cli -p "$REDIS_PORT" PING >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "请手动启动 Redis (port $REDIS_PORT)"
}

PASS=0
FAIL=0
record() { "$@" && PASS=$((PASS + 1)) || FAIL=$((FAIL + 1)); }

echo "=== Redis 降级验收 BASE_URL=$BASE_URL ==="
echo ""

stop_redis
echo "等待 3s（清除 DegradeService 2s 缓存）..."
sleep 3

echo ""
echo "--- Redis 关闭后（Tomcat 未重启）---"
record check_http "首页可访问" "$BASE_URL/" "二手"
record check_http "商品列表" "$BASE_URL/product/list" ""
record check_http "降级推荐 L1" "$BASE_URL/test/degrade/recommend?userId=1&limit=5" '"success":true'
record check_http "降级推荐 redisAvailable=false" "$BASE_URL/test/degrade/recommend?userId=1&limit=3" '"redisAvailable":false'
record check_http "监控指标" "$BASE_URL/monitor/metrics" "degradeL1"
record check_http "Session 写入" "$BASE_URL/test/session/set?key=degrade&value=ok" '"success":true'

echo ""
start_redis
echo ""
echo "=== 结果: PASS=$PASS FAIL=$FAIL ==="
[[ "$FAIL" -eq 0 ]]
