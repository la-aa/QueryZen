#!/usr/bin/env bash
# 将初始化脚本灌入 Oracle 容器并执行（需容器已通过 docker compose up 启动）
set -euo pipefail
cd "$(dirname "$0")/.."

echo "copying script into container..."
docker exec -i queryzen-oracle bash -c 'cat > /tmp/setup.sql' < infra/oracle_readonly_setup.sql

echo "executing as SYSTEM..."
docker exec queryzen-oracle bash -c \
  'sqlplus -s system/"QueryZen#2026"@//localhost:1521/FREEPDB1 @/tmp/setup.sql'

echo "done."