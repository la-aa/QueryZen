<div align="center">

# QueryZen

**面向监管合规场景的只读数据库查询工具**

Read-only database query tool designed for regulatory compliance.

![License](https://img.shields.io/badge/license-MIT-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![React](https://img.shields.io/badge/React-18-61dafb)

Oracle · React · TypeScript · Docker

</div>

---

## 特性 (Features)

- **多层只读防御**：数据库账号、连接、SQL 解析、资源限额四层防线，物理级别无法写入。
- **审计哈希链**：独立审计库由数据库触发器维护 SHA-256 哈希链，篡改任何历史记录即断链，并提供完整性校验 API。
- **防拖库**：查询自动追加 `ROWNUM <= N` 行数限制 + 超时 + `setMaxRows` 三重保险。
- **友好的 SQL 编辑**：Monaco 编辑器，支持**选中即执行**、多语句以 `;` 分隔**并行执行**、逐条独立结果与 XLSX 导出。
- **现代化的界面**：全套 SVG 图标、空状态与操作引导，登录 / 改密 / 查询 / 审计 / 用户管理均带描述说明。
- **账号与权限**：admin 专属创建账号、密码 30 天有效期与过期强制更新、忘记密码由管理员重置。
- **多数据库可扩展**：方言抽象，新增 MySQL / PostgreSQL 只需实现一个 `Dialect`。

## 安全设计 (Security Model)

1. **账号层**：Oracle 只读账号仅授予 `CREATE SESSION` + `SELECT`，无任何 DML/DDL 权限。
2. **连接层**：HikariCP 连接强制 `readOnly=true`。
3. **应用层**：Druid SQLParser 解析为 AST，仅放行 `SELECT / WITH ... SELECT`，拦截 DDL/DML/存储过程/多语句/`FOR UPDATE`。
4. **资源层**：`setQueryTimeout` 超时 + 自动行数改写 + `setMaxRows`，防止全表扫描拖垮数据库。
5. **审计层**：独立 `AUDIT_OWNER` 库，写入账号仅 `INSERT`，数据库触发器维护 SHA-256 哈希链。

## 技术栈 (Tech Stack)

- **后端**：Java 17 · Spring Boot 3.4 · Maven · Druid SQLParser · HikariCP · Oracle JDBC · Apache POI (XLSX)
- **前端**：React 18 · TypeScript · Vite · Monaco Editor · AG Grid
- **数据库**：Oracle 23 Free（本地开发用 Docker `gvenzl/oracle-free:23-slim`）

## 快速开始 (Getting Started)

**环境要求**：JDK 17、Maven 3.8+、Docker（或 Colima）。

### 一键启动

```bash
bash scripts/dev.sh
```

依次完成：启动 Oracle 容器 → 初始化（示例数据 / 只读账号 / 审计库）→ 启动后端(8080) → 启动前端(5173)。

### 手动分步

```bash
# 1. 启动 Oracle 测试库
docker compose up -d
docker logs -f queryzen-oracle   # 看到 "DATABASE IS READY TO USE!" 后 Ctrl+C

# 2. 初始化数据、只读账号与审计库（可重复执行）
bash scripts/setup-oracle.sh

# 3. 启动后端
cd backend && source ../scripts/env.sh && mvn spring-boot:run

# 4. 启动前端（新开终端）
cd frontend && npm install && npm run dev
```

### 默认账号

```
用户名: admin
密码:   admin
```

> ⚠️ 默认凭据仅用于本地开发，生产部署必须通过环境变量注入真实凭据。

## IDE 配置 (IntelliJ IDEA)

项目为「后端 Maven + 前端 Vite」双模块结构，用 IDEA 打开**仓库根目录**：
后端会自动识别 `backend/pom.xml`（Maven 模块），前端识别 `frontend/package.json`（`Run` 面板选择 `dev` 脚本）。

常见报错与解决：

| 现象 | 原因 | 解决 |
|---|---|---|
| `invalid target release: 17` / `Error: java: ... 无效的源发行版` | 项目的 Maven Runner 或 Project SDK 指向了旧 JDK 1.8 | 安装 JDK 17 后：`Settings → Build, Execution, Deployment → Build Tools → Maven → Runner → JRE` 选择 **17**；`File → Project Structure → Project → SDK` 选 **17** |
| 依赖标红 / `Cannot resolve symbol` | Maven 未成功导入 | 确认 Maven Home 为 **3.8+**（Homebrew `/opt/homebrew` 自带 3.9.x，勿用 3.3）；`Maven 工具窗 → Reload All Maven Projects` |
| 前端 Node 标红 | 未选 Node 解释器 | `Settings → Languages & Frameworks → Node.js → Node interpreter` 选择 `which node` 路径 |
| `target/` `dist/` 显示在项目树 | 构建产物 | 已是 `.gitignore` 忽略项；如需隐藏：`Settings → Editor → File Types → Ignore files and folders` 添加 `target;dist` |

本地默认 `mvn`/`java` 已固定为 JDK 17 + Maven 3.9（见 `scripts/env.sh`）。

## 用户与认证 (Users & Authentication)

应用账号存储在审计库 `AUDIT_OWNER.USERS` 表中；登录密码为 SHA-256 存储。

| 能力 | 说明 |
|---|---|
| 创建账号 | 仅 **admin** 可在「用户管理」页创建，可指定 `admin` / `user` 角色 |
| 密码有效期 | 默认 **30 天**；过期后登录仅能被放行到「更新密码」，其余接口一律 403 |
| 忘记密码 | 由管理员在「用户管理」页重置为临时密码，重置后首次登录强制改密 |
| 最小权限 | 写入账号仅 `INSERT/UPDATE`（USERS 表），读取账号仅 `SELECT` |

## 界面概览 (UI Overview)

系统为浏览器单页应用，图标化、带空状态与操作指引，分级展示：

| 页面 | 说明 |
|---|---|
| **登录 / 强制改密** | 密码可视切换；密码过期后强制更新，未更新前其余功能不可用 |
| **查询** | 连接选择、表清单（点击即生成 SELECT）、Monaco SQL 编辑器、多结果卡片（行数 / 耗时 / 截断提示 / 独立导出 XLSX） |
| **审计日志** | 查询与导出留痕，一键哈希链完整性校验；空状态有引导文案 |
| **用户管理**（仅 admin 可见） | 创建账号（角色可选）、重置密码；空状态与表单均带说明 |

## API 一览 (API Reference)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回 token 及 `passwordExpired` |
| POST | `/api/auth/change-password` | 修改密码（过期会话专属许可） |
| GET/POST | `/api/auth/users` | 列出 / 创建用户（仅 admin） |
| POST | `/api/auth/users/reset-password` | 重置用户密码（仅 admin） |
| GET | `/api/connections` | 列出可用连接 |
| GET | `/api/connections/{id}/tables` | 列出连接的可见表 |
| POST | `/api/query` | 执行只读查询 |
| POST | `/api/query/export` | 查询结果导出 XLSX（记入审计） |
| GET | `/api/audit/verify` | 审计哈希链完整性校验 |
| GET | `/api/audit/entries` | 查询审计日志 |

## 验证只读防线 (Verification)

以 `queryzen_ro` 登录数据库：

```sql
SELECT * FROM demo.employees;                        -- 成功
INSERT INTO demo.employees VALUES (99,'x','x',0);    -- ORA-01031 拒绝
```

通过页面/API 执行 `DELETE FROM demo.employees`，后端应返回 `语句类型不被允许`。

## 生产部署 (Production Deployment)

镜像采用多阶段构建：后端（Maven → JRE）、前端（Node → Nginx 托管静态资源 + 反代 API）。

```bash
# 1. 准备真实配置（勿提交凭据）
cp config/application-prod.example.yml config/application-prod.yml
cp .env.prod.example .env.prod
chmod 600 .env.prod config/application-prod.yml

# 2. 生成密码哈希并填入 application-prod.yml
echo -n "你的新密码" | shasum -a 256 | cut -d' ' -f1

# 3. 构建并启动
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
```

### 生产安全清单

- [ ] 数据库连接使用只读账号（`GRANT CONNECT, SELECT`）
- [ ] 审计写入账号仅 `INSERT`，无 UPDATE/DELETE/TRUNCATE
- [ ] 开启 Oracle Unified Auditing，二次留痕独立于应用
- [ ] 敏感凭据仅存于 `.env.prod` / 密钥管理，不入库、不入镜像
- [ ] 前置 HTTPS 反向代理（Nginx/Traefik），强制 TLS
- [ ] 定期执行 `GET /api/audit/verify` 校验并归档

## 目录结构 (Directory Layout)

```
QueryZen/
├─ docker-compose.yml                 # 本地 Oracle 测试库
├─ infra/oracle_readonly_setup.sql    # 建示例数据 + 只读账号 + 审计库（哈希链触发器）
├─ backend/                           # Spring Boot 后端
│  └─ src/main/java/com/queryzen/
│     ├─ config/                      # 连接注册 + 认证 + 用户管理
│     ├─ dialect/                     # 多数据库方言抽象（Oracle 为首个实现）
│     ├─ engine/                      # 只读 SQL 校验 / 行数改写 / 查询执行
│     ├─ audit/                       # 哈希链审计写入 + 完整性校验
│     └─ web/                         # REST 接口
├─ frontend/                          # React 前端
└─ scripts/                           # 本地开发/部署脚本
```

## 路线图 (Roadmap)

- [ ] **SSO / LDAP 集成**（优先项：接入公司身份目录，本地账号仅作兜底）
- [ ] 统一审计（Oracle Unified Audit）策略脚本
- [ ] 敏感表审批流 + 列级脱敏
- [ ] 新增 MySQL / PostgreSQL 方言实现

## 参与贡献 (Contributing)

欢迎提交 Issue 与 PR。开发流程：`fork` → 基于 `main` 开分支 → 开发并补充测试 → 提交 PR。

## 许可证 (License)

[MIT](LICENSE)

---

> 安全提示：本项目不应绕过任何已审计的查询接口去直连生产库；审计链一旦断裂应立即排查。