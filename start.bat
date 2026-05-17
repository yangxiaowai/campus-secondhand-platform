@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo [成员6] 启动 MySQL、Redis、MinIO 与双 Tomcat（8080 / 8081）...
REM 必须 --build，否则 Docker 可能沿用旧镜像（缺少 UserProfileController 等类会导致 /user/inbox 404）
docker compose up -d --build
if errorlevel 1 (
    echo 启动失败。请确认已安装 Docker Desktop 并已启动。
    pause
    exit /b 1
)

echo.
echo 应用: http://localhost:8080   与   http://localhost:8081
echo MySQL 映射到本机端口 3307，用户 root，密码见 docker\campus-db.properties / docker-compose.yml
echo Redis 本机端口: localhost:6380 （映射到容器内 6379）
echo MinIO 控制台: http://localhost:9001   账号 minioadmin / minioadmin
echo.
echo 停止: docker compose down
pause
