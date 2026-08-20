#!/usr/bin/env bash
# 验证 QueryZen 本地 Oracle 初始化的只读防线与审计哈希链
set -uo pipefail
cd "$(dirname "$0")/.."

PW='QueryZen#2026'
SRV='//localhost:1521/FREEPDB1'

run_sql() { # user sql_text
  local user="$1"
  shift
  printf '%s\n' "$@" | docker exec -i queryzen-oracle sqlplus -s "${user}/\"${PW}\"@${SRV}" 2>&1
}

echo "===== 1) 只读账号：查询授权表应成功 ====="
run_sql queryzen_ro 'SELECT * FROM demo.employees;' 'SELECT * FROM demo.orders;'

echo
echo "===== 2) 只读账号：写入应被拒绝 ORA-01031 ====="
run_sql queryzen_ro "INSERT INTO demo.employees VALUES (99,'x','x','x',0,'x');"

echo
echo "===== 3) 只读账号：未授权表应不可见 ORA-00942 ====="
run_sql queryzen_ro 'SELECT * FROM demo.payroll;'

echo
echo "===== 4) 审计写入：应成功并自动生成哈希链 ====="
run_sql audit_writer "INSERT INTO audit_owner.audit_log (username,ip,sql_text,rows_returned,elapsed_ms,error_msg,content) VALUES ('tester','127.0.0.1','SELECT 1 FROM dual',1,1,NULL,'tester|127.0.0.1|1|1||cc1007ad6bf281f36d1c916b80b19c7e21d999e401da8f0a9ca1e6df2a3d160b');" 'COMMIT;'

echo
echo "===== 5) 审计篡改/删除：应被拒绝（无权限） ====="
run_sql audit_writer "UPDATE audit_owner.audit_log SET content='tampered' WHERE seq=1;" "DELETE FROM audit_owner.audit_log WHERE seq=1;"

echo
echo "===== 6) 审计读取：应看到链条 ====="
run_sql audit_reader 'SELECT seq, prev_hash, hash FROM audit_owner.audit_log ORDER BY seq;'

echo
echo "验证完成"