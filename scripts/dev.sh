#!/usr/bin/env bash
# 一键启动 QueryZen 本地开发环境（Oracle + 后端 + 前端）
set -euo pipefail
cd "$(dirname "$0")/.."

# 1. 启动 Oracle 测试库
echo "==> 启动 Oracle 容器..."
docker compose up -d
echo "==> 等待数据库就绪（首次约 2-3 分钟）..."
for i in $(seq 1 36); do
  ready=$(docker logs queryzen-oracle 2>&1 | grep -c "DATABASE IS READY TO USE!" || true)
  [ "$ready" -ge 1 ] && break
  sleep 5
done
if [ "$ready" -eq 0 ]; then
  echo "Oracle 启动过久，请执行 docker logs queryzen-oracle 查看进度"
  exit 1
fi
echo "==> 初始化数据与只读账号：bash scripts/setup-oracle.sh"
bash scripts/setup-oracle.sh

# 2. 启动后端
echo "==> 启动后端 (8080)..."
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
nohup mvn -f backend/pom.xml -DskipTests spring-boot:run > /tmp/queryzen-backend.log 2>&1 &
BACKEND_PID=$!
echo "backend pid=$BACKEND_PID (log: /tmp/queryzen-backend.log)"

# 3. 启动前端
echo "==> 启动前端 (5173)..."
nohup npm --prefix frontend run dev > /tmp/queryzen-frontend.log 2>&1 &
FRONTEND_PID=$!
echo "frontend pid=$FRONTEND_PID (log: /tmp/queryzen-frontend.log)"

echo
echo "访问 http://localhost:5173  (admin / admin)"
echo "停止: kill $BACKEND_PID $FRONTEND_PID; docker compose down"