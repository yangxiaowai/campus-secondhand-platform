#!/usr/bin/env bash
# 压测时观察 Redis connected_clients 与 Tomcat/Jedis 容量是否匹配
# 用法：
#   ./scripts/redis-pool-watch.sh              # 本机 Redis 6379
#   REDIS_PORT=6380 ./scripts/redis-pool-watch.sh   # Docker 映射端口
#   INTERVAL=2 ./scripts/redis-pool-watch.sh        # 每 2 秒刷新

set -euo pipefail

REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
INTERVAL="${INTERVAL:-3}"

# Tomcat 9 官方镜像未改 server.xml 时 HTTP 连接器默认值
TOMCAT_MAX_THREADS="${TOMCAT_MAX_THREADS:-200}"
TOMCAT_INSTANCES="${TOMCAT_INSTANCES:-2}"
JEDIS_MAX_TOTAL_PER_JVM="${JEDIS_MAX_TOTAL_PER_JVM:-400}"

THEORETICAL_MAX_CLIENTS=$((JEDIS_MAX_TOTAL_PER_JVM * TOMCAT_INSTANCES))
RECOMMENDED_POOL_PER_JVM=$((TOMCAT_MAX_THREADS + TOMCAT_MAX_THREADS / 5))  # maxThreads + 20%

echo "=== 容量对照（静态）==="
echo "Tomcat maxThreads（单实例，默认）     : ${TOMCAT_MAX_THREADS}"
echo "Tomcat 实例数（Compose 双实例）       : ${TOMCAT_INSTANCES}"
echo "集群最大工作线程（约）                : $((TOMCAT_MAX_THREADS * TOMCAT_INSTANCES))"
echo "Jedis maxTotal（单 JVM，来自配置）    : ${JEDIS_MAX_TOTAL_PER_JVM}"
echo "Jedis 理论峰值连接（两实例池打满）  : ${THEORETICAL_MAX_CLIENTS}"
echo "建议单实例 maxTotal（maxThreads+20%）: ${RECOMMENDED_POOL_PER_JVM}"
echo ""
echo "判定：单实例 maxTotal(${JEDIS_MAX_TOTAL_PER_JVM}) >= maxThreads(${TOMCAT_MAX_THREADS}) → $(
  if [ "${JEDIS_MAX_TOTAL_PER_JVM}" -ge "${TOMCAT_MAX_THREADS}" ]; then echo "匹配（有余量）"; else echo "偏小，压测可能池耗尽"; fi
)"
echo "判定：双实例理论峰值(${THEORETICAL_MAX_CLIENTS}) 应 < Redis maxclients → 压测时看下方实时值"
echo ""
echo "=== 实时 Redis（${REDIS_HOST}:${REDIS_PORT}，每 ${INTERVAL}s）==="
echo "Ctrl+C 结束"
echo ""

while true; do
  if ! redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" PING >/dev/null 2>&1; then
    echo "$(date '+%H:%M:%S')  Redis 不可达"
    sleep "${INTERVAL}"
    continue
  fi

  INFO=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" INFO clients 2>/dev/null)
  CONNECTED=$(echo "${INFO}" | awk -F: '/^connected_clients:/{print $2}' | tr -d '\r')
  MAXCLIENTS=$(echo "${INFO}" | awk -F: '/^maxclients:/{print $2}' | tr -d '\r')
  BLOCKED=$(echo "${INFO}" | awk -F: '/^blocked_clients:/{print $2}' | tr -d '\r')

  # 来自应用的连接（排除 redis-cli / 监控自身）
  APP_CLIENTS=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CLIENT LIST 2>/dev/null \
    | grep -cv 'cmd=client' || true)

  UTIL_PCT=0
  if [ -n "${CONNECTED}" ] && [ "${THEORETICAL_MAX_CLIENTS}" -gt 0 ]; then
    UTIL_PCT=$((CONNECTED * 100 / THEORETICAL_MAX_CLIENTS))
  fi

  echo "$(date '+%H:%M:%S')  connected_clients=${CONNECTED}  app≈${APP_CLIENTS}  blocked=${BLOCKED}  占理论峰值${UTIL_PCT}%  maxclients=${MAXCLIENTS}"

  if [ -n "${CONNECTED}" ] && [ "${CONNECTED}" -gt "${THEORETICAL_MAX_CLIENTS}" ]; then
    echo "  ⚠ connected_clients 超过 Jedis 理论峰值，检查是否有其它客户端或连接泄漏"
  fi
  if [ -n "${CONNECTED}" ] && [ "${CONNECTED}" -gt $((TOMCAT_MAX_THREADS * TOMCAT_INSTANCES * 2)) ]; then
    echo "  ⚠ 连接数明显高于 2×集群工作线程，建议查连接未归还或第三方客户端"
  fi

  sleep "${INTERVAL}"
done
