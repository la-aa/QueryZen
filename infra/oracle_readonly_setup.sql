-- ============================================================================
-- QueryZen 初始化脚本（以 SYSTEM 登录 FREEPDB1 执行，可重复运行）
-- 1) 创建 DEMO 示例数据 schema
-- 2) 创建只读账号 queryzen_ro（仅 SELECT，物理不可写）
-- 3) 创建防篡改审计库（AUDIT_OWNER + 仅 INSERT 的写入账号 + 哈希链触发器）
-- ============================================================================

-- 清理旧对象（幂等）
BEGIN
  EXECUTE IMMEDIATE 'DROP USER demo CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
  EXECUTE IMMEDIATE 'DROP USER queryzen_ro CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
  EXECUTE IMMEDIATE 'DROP USER audit_owner CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
  EXECUTE IMMEDIATE 'DROP USER audit_writer CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
  EXECUTE IMMEDIATE 'DROP USER audit_reader CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================================================
-- 1) DEMO 示例数据
-- ============================================================================
CREATE USER demo IDENTIFIED BY demo;
GRANT CONNECT, RESOURCE TO demo;
ALTER USER demo QUOTA UNLIMITED ON USERS;

CREATE TABLE demo.employees (
  id       NUMBER PRIMARY KEY,
  name     VARCHAR2(100),
  phone    VARCHAR2(20),
  id_card  VARCHAR2(18),
  salary   NUMBER,
  dept     VARCHAR2(50)
);

CREATE TABLE demo.orders (
  id        NUMBER PRIMARY KEY,
  emp_id    NUMBER,
  amount    NUMBER,
  ordered_at DATE
);

-- 敏感表（故意不对 queryzen_ro 授权 -> 查询将 ORA-00942 不可见）
CREATE TABLE demo.payroll (
  id      NUMBER PRIMARY KEY,
  emp_id  NUMBER,
  bank_no VARCHAR2(30),
  amount  NUMBER
);

INSERT INTO demo.employees VALUES (1,'张三','13800000001','110101199001010011',30000,'研发部');
INSERT INTO demo.employees VALUES (2,'李四','13800000002','110101199202020022',25000,'研发部');
INSERT INTO demo.employees VALUES (3,'王五','13800000003','110101199303030033',22000,'市场部');
INSERT INTO demo.employees VALUES (4,'赵六','13800000004','110101199404040044',20000,'财务部');

INSERT INTO demo.orders VALUES (101,1,9999.50,DATE'2026-01-01');
INSERT INTO demo.orders VALUES (102,1,1200.00,DATE'2026-02-01');
INSERT INTO demo.orders VALUES (103,2,4500.75,DATE'2026-03-01');
INSERT INTO demo.orders VALUES (104,3,800.00, DATE'2026-04-01');

COMMIT;

-- ============================================================================
-- 2) 只读账号 queryzen_ro
--    仅 CREATE SESSION + 指定表的 SELECT；无任何 INSERT/UPDATE/DELETE/DDL
-- ============================================================================
CREATE USER queryzen_ro IDENTIFIED BY "QueryZen#2026";
GRANT CREATE SESSION TO queryzen_ro;
GRANT SELECT ON demo.employees TO queryzen_ro;
GRANT SELECT ON demo.orders    TO queryzen_ro;
-- 注意：demo.payroll 不授权 -> 对 queryzen_ro 不可见

-- ============================================================================
-- 3) 防篡改审计库
--    审计表由审计_owner 拥有；写入账号仅 INSERT，无 UPDATE/DELETE/TRUNCATE/ALTER。
--    触发器在插入时自动维护 SHA-256 哈希链（standard_hash），篡改任一历史行即断链。
-- ============================================================================
CREATE USER audit_owner IDENTIFIED BY "QueryZen#2026";
GRANT CONNECT, RESOURCE TO audit_owner;
ALTER USER audit_owner QUOTA UNLIMITED ON USERS;

CREATE TABLE audit_owner.audit_log (
  seq           NUMBER PRIMARY KEY,
  ts            TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
  username      VARCHAR2(128),
  ip            VARCHAR2(64),
  sql_text      CLOB,
  rows_returned NUMBER,
  elapsed_ms    NUMBER,
  params_json   CLOB,
  error_msg     CLOB,
  prev_hash     VARCHAR2(64),
  hash          VARCHAR2(64),
  content       VARCHAR2(4000)
);

CREATE SEQUENCE audit_owner.audit_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE audit_owner.audit_head (
  id        NUMBER PRIMARY KEY,
  last_hash VARCHAR2(64)
);

INSERT INTO audit_owner.audit_head VALUES (1, 'GENESIS');
COMMIT;

-- 哈希链触发器：在 INSERT 时由数据库维护 prev_hash / hash（定义者权限）
CREATE OR REPLACE TRIGGER audit_owner.trg_audit_chain
BEFORE INSERT ON audit_owner.audit_log
FOR EACH ROW
DECLARE
  v_prev VARCHAR2(64);
  v_hash RAW(32);
BEGIN
  -- 串行化取上一哈希（单行头表 + 行锁天然串行）
  UPDATE audit_owner.audit_head SET last_hash = last_hash
   RETURNING last_hash INTO v_prev;
  SELECT LOWER(RAWTOHEX(STANDARD_HASH(v_prev || :NEW.content, 'SHA256'))) INTO v_hash FROM dual;
  :NEW.seq       := audit_owner.audit_seq.NEXTVAL;
  :NEW.ts        := SYSTIMESTAMP;
  :NEW.prev_hash := v_prev;
  :NEW.hash      := LOWER(RAWTOHEX(v_hash));
  UPDATE audit_owner.audit_head SET last_hash = :NEW.hash;
END;
/

-- 审计写入账号：仅 INSERT（无 UPDATE/DELETE/TRUNCATE/ALTER）
CREATE USER audit_writer IDENTIFIED BY "QueryZen#2026";
GRANT CREATE SESSION TO audit_writer;
GRANT INSERT ON audit_owner.audit_log TO audit_writer;

-- 审计只读账号：仅 SELECT（用于完整性校验与查询）
CREATE USER audit_reader IDENTIFIED BY "QueryZen#2026";
GRANT CREATE SESSION TO audit_reader;
GRANT SELECT ON audit_owner.audit_log TO audit_reader;

-- ============================================================================
-- 4) 应用用户表（账号管理）
--    仅 admin 可创建账号；写入走 audit_writer（INSERT/UPDATE），查询走 audit_reader（仅 SELECT）。
--    初始内建 admin 账号与配置文件 `queryzen.users` 一致（密码 admin），密码为 SHA-256。
--    pwd_changed_at 记录上次改密时间，用于 30 天密码有效期校验。
-- ============================================================================
CREATE TABLE audit_owner.users (
  username         VARCHAR2(64) PRIMARY KEY,
  password_sha256  VARCHAR2(64) NOT NULL,
  roles            VARCHAR2(256),
  created_by       VARCHAR2(64),
  created_at       TIMESTAMP DEFAULT SYSTIMESTAMP,
  pwd_changed_at   TIMESTAMP DEFAULT SYSTIMESTAMP
);

GRANT SELECT, INSERT, UPDATE ON audit_owner.users TO audit_writer;
GRANT SELECT ON audit_owner.users TO audit_reader;

INSERT INTO audit_owner.users (username, password_sha256, roles, created_by)
VALUES ('admin', '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', 'admin', 'SYSTEM');
COMMIT;

-- ============================================================================
-- 验证方法（在 DBeaver 中手动执行）
-- ============================================================================
-- 以 queryzen_ro 连接：
--   SELECT * FROM demo.employees;                 -- 成功
--   INSERT INTO demo.employees VALUES (99,'x','x',0);   -- ORA-01031 拒绝
--   SELECT * FROM demo.payroll;                   -- ORA-00942 不可见
--
-- 以 audit_writer 连接:
--   INSERT INTO audit_owner.audit_log (username,ip,sql_text,rows_returned,elapsed_ms,content)
--     VALUES ('tester','127.0.0.1','SELECT 1',1,1,'tester|127.0.0.1|1|1|<sql_sha256_hex>');  -- 成功, 自动生成哈希
--   UPDATE audit_owner.audit_log SET content='tampered' WHERE seq=1;  -- ORA-01031 拒绝
--   DELETE FROM audit_owner.audit_log WHERE seq=1;                     -- ORA-01031 拒绝