# QueryZen

面向监管合规场景的**只读数据库查询工具**。以多语言构建的策略使得未来可以扩展到 MySQL/PostgreSQL 等其它数据库。当前版本为 Oracle 首个落地实现。

## 核心安全设计（多层只读防御）

1. **账号层**：Oracle 只读账号仅授予 `CREATE SESSION` + `SELECT`，无任何 DML/DDL 权限，数据库层面物理不可写。
2. **连接层**：HikariCP 连接强制 `readOnly=true`。
3. **应用层**：Druid SQLParser 解析 SQL 为 AST，只放行 `SELECT / WITH ... SELECT`，拦截 DDL/DML 存储过程/多语句/`FOR UPDATE`。
4. **资源层**：`setQueryTimeout` 超时 + 自动 `ROWNUM <= N` 行数改写 + `setMaxRows` 双保险，防止拖爆全表。
5. **审计层**：独立 `AUDIT_OWNER` 库，专用账号仅授 `INSERT`（无 UPDATE/DELETE/TRUNCATE），数据库触发器维护 SHA-256 哈希链，篡改任何历史记录即断链，并提供链完整性校验 API。

## 技术栈

- 后端：Java 17 + Spring Boot 3.4 + Maven + Druid SQLParser + HikariCP + Oracle JDBC + Apache POI（XLSX 导出）
- 前端：React 18 + TypeScript + Vite + Monaco Editor + AG Grid
- 数据库：Oracle（Docker `gvenzl/oracle-free:23-slim` 本地开发库）

## 目录结构

```
QueryZen/
├─ docker-compose.yml                 # 一键启动本地 Oracle 测试库
├─ infra/oracle_readonly_setup.sql    # 建示例数据 + 只读账号 + 审计库（含哈希链触发器）
├─ backend/                           # Spring Boot 后端
│  └─ src/main/java/com/queryzen/
│     ├─ config/                      # 连接注册 + 认证
│     ├─ dialect/                     # 多数据库方言抽象（Oracle 为首个实现）
│     ├─ engine/                      # 只读 SQL 校验 / 行数改写 / 查询执行
│     ├─ audit/                       # 哈希链审计写入 + 完整性校验
│     └─ web/                         # REST 接口
└─ frontend/                          # React 前端
```

## 本地启动

前置依赖：JDK 17、Maven、Docker（本机 Colima 已装好）。

### 1. 一键启动（推荐）

```bash
bash scripts/dev.sh
```

脚本会依次完成：启动 Oracle 容器 → 执行初始化脚本（建示例数据、只读账号、审计库）→ 启动后端(8080) → 启动前端(5173)。

### 2. 手动分步启动

```bash
# 启动 Oracle 测试库
docker compose up -d
docker logs -f queryzen-oracle   # 看到 "DATABASE IS READY TO USE!" 后 Ctrl+C

# 初始化数据与只读账号 + 审计库
bash scripts/setup-oracle.sh      # 可重复执行（幂等）

# 启动后端
cd backend && source ../scripts/env.sh && mvn spring-boot:run

# 启动前端（新开一个终端）
cd frontend && npm install && npm run dev
```

连接信息：host `localhost` / 端口 `1521` / 服务名 `FREEPDB1` / 用户 `SYSTEM` / 密码 `QueryZen#2026`。

### 5. 登录

默认账号（密码 `admin`，配置中存的是 SHA-256 哈希）：

```
用户名: admin
密码:   admin
```

## 验证只读防线（核心验收项）

用 DBeaver 以 `queryzen_ro` 登录后执行：

```sql
-- 应该可以查询
SELECT * FROM demo.employees;

-- 应该报 ORA-01031：insufficient privileges（写不进去！）
INSERT INTO demo.employees VALUES (99,'x','x',0);
```

再通过前端/API 尝试执行 `DELETE FROM demo.employees`，后端应返回："语句类型不被允许：Delete"。

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 登录获取 token |
| GET  | /api/connections | 列出可用连接 |
| GET  | /api/connections/{id}/tables | 列出当前连接中可见的表（仅授权表，侧栏展示用） |
| POST | /api/query       | 执行只读查询 |
| POST | /api/query/export | 查询结果导出为 XLSX（操作记入审计，标记 `[EXPORT]`） |
| GET  | /api/audit/verify | 审计哈希链完整性校验 |
| GET  | /api/audit/entries | 查询审计日志 |

## 后续里程碑

- [ ] 统一审计（Oracle Unified Audit）策略脚本
- [ ] SSO/LDAP 集成
- [ ] 敏感表审批流 + 列级脱敏
- [ ] 导出留痕
- [ ] 新增 MySQL / PostgreSQL 方言实现
- [ ] Docker 化部署 + 验收

> 注意：配置中的密码默认值仅用于本地开发，生产部署必须通过环境变量注入，勿提交真实凭据。