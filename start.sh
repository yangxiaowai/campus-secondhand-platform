#!/usr/bin/env sh
# 成员6：Linux / macOS 一键启动（需已安装 Docker 与 docker compose 插件）
set -e
cd "$(dirname "$0")"

echo "[成员6] 启动 MySQL、Redis、MinIO 与双 Tomcat（8080 / 8081）..."
docker compose up -d --build

echo ""
echo "应用: http://localhost:8080  与  http://localhost:8081"
echo "MySQL 本机端口 3307，用户 root，密码见 docker/campus-db.properties"
echo "Redis 本机端口: localhost:6380 （映射到容器内 6379）"
echo "MinIO 控制台: http://localhost:9001 (minioadmin / minioadmin)"
echo ""
echo "停止: docker compose down"
