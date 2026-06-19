#!/usr/bin/env bash
# 成员D 验收脚本：降级容错 + 监控看板
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PASS=0
FAIL=0

check() {
  local name="$1"
  local ok="$2"
  if [[ "$ok" == "1" ]]; then
    echo "[PASS] $name"
    PASS=$((PASS + 1))
  else
    echo "[FAIL] $name"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== 成员D 验收 (BASE_URL=$BASE_URL) ==="

# 1 Redis
if curl -sf "$BASE_URL/test/redis" | grep -q '"success":true'; then
  check "Redis 连通" 1
else
  check "Redis 连通" 0
fi

# 2 monitor metrics
if curl -sf "$BASE_URL/monitor/metrics" | grep -q 'degradeL1'; then
  check "监控指标 API" 1
else
  check "监控指标 API" 0
fi

# 3 monitor html
code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/monitor.html")
[[ "$code" == "200" ]] && check "监控页面" 1 || check "监控页面" 0

# 4 degrade recommend (redis up)
body=$(curl -sf "$BASE_URL/test/degrade/recommend?userId=1&limit=5" || true)
if echo "$body" | grep -q '"success":true' && echo "$body" | grep -q '"count"'; then
  check "降级推荐(Redis正常)" 1
else
  check "降级推荐(Redis正常)" 0
fi

# 5 L2 cold start
body=$(curl -sf "$BASE_URL/test/degrade/recommend?userId=99999&limit=5" || true)
if echo "$body" | grep -q '"success":true'; then
  check "L2 冷启动" 1
else
  check "L2 冷启动" 0
fi

# 6 metrics snapshot
if curl -sf "$BASE_URL/test/degrade/metrics" | grep -q 'degradeL2'; then
  check "指标快照 API" 1
else
  check "指标快照 API" 0
fi

echo ""
echo "=== 结果: PASS=$PASS FAIL=$FAIL ==="
[[ "$FAIL" -eq 0 ]]
