# ForgePilot

ForgePilot（云端 AI Coding 平台）以需求为入口，调度 AI Agent 在隔离环境中修改代码，再由平台独立验证、人工审核后完成交付。平台负责编排、证据和交付；Agent 只负责授权范围内的读写和命令执行。

一个项目关联多个 Git 仓库。一次任务使用一个工作区目录，平台把这些仓库都检出到该目录里。MVP 的 Agent 运行时是容器内的 Codex CLI。模型长期密钥留在执行节点的代理侧。

当前仓库在 `dev` 分支推进 V1.2：已接通本地登录与 JWT、个人工作台、项目/多仓库、需求、任务、模型/编码工具目录、三层规约与合成快照、操作记录查询与日报，以及 GitHostingPort、多仓检出、验证/Draft MR 交付、Agent Session、Responses 模型代理（含真实上游转发/占位 stub）、Codex CLI 适配器、执行节点注册/心跳/排队、ContainerRuntime（Fake+可选 Docker）、Session 高频日志流（Redis/内存）、事务性 Outbox（控制/状态事件，默认内存假 broker，可选 RabbitMQ）、MinIO/S3 日志归档（默认内存假存储）、GIT_ASKPASS 短时令牌、工作区保留策略、Docker `network=none` 默认与 allowlist 开关。无 `codex` 时不假装编码成功。

## 文档

产品、架构和后续设计文档都在 [`docs/`](docs/README.md)。除本文件和 [`AGENTS.md`](AGENTS.md) 外，不要在仓库根目录放置独立设计文档。

- [文档索引与分类规则](docs/README.md)
- [MVP 需求规格说明书 V1.2](docs/requirements/mvp-prd.md)
- [系统基础架构设计说明书 V1.2](docs/architecture/system-architecture.md)

## 仓库结构

- `forgepilot-server/`：Spring Boot 3 控制面，包为 `system`、`project`、`execution`、`delivery`。
- `forgepilot-execution/`：独立进程的执行服务。
- `forgepilot-web/`：Vue 3 工作台。
- `docker-compose.yml`：本机 PostgreSQL、Redis、RabbitMQ、MinIO。

## 构建、测试与运行

Java 源级别是 17，用 JDK 17 或更高版本编译。在仓库根目录：

```bash
docker compose up -d
mvn test
mvn -pl forgepilot-server spring-boot:run
mvn -pl forgepilot-execution spring-boot:run
npm ci --prefix forgepilot-web
npm run typecheck --prefix forgepilot-web
npm run build --prefix forgepilot-web
npm run dev --prefix forgepilot-web
```

控制面监听 `8080`，执行服务监听 `8081`，前端开发服务器监听 `5173` 并把 `/api` 代理到控制面。

- 控制面健康检查：`http://localhost:8080/api/v1/health`
- 执行服务健康检查：`http://localhost:8081/api/v1/health`
- 模型目录占位：`http://localhost:8080/api/v1/model-providers` 返回 501，不返回密钥
- 控制面启动会跑 Flyway，因此需要 Compose 里的 PostgreSQL。执行服务不连接业务库。

Compose 里的账号口令只用于本机开发：PostgreSQL 和 RabbitMQ 是 `forgepilot` / `forgepilot`，MinIO 是 `forgepilot` / `forgepilot-dev`。不要把它们用于共享环境。

## 本机配置要点（docker-compose）

Compose 只起基础设施；控制面通过环境变量/ `application.yml` 开关对接：

| 能力 | 默认（`mvn test` 友好） | 打开真实依赖 |
| --- | --- | --- |
| RabbitMQ 控制事件 | `FORGEPILOT_RABBIT_ENABLED=false`（Outbox → 内存假 broker） | `true`，并保证 `rabbitmq` 健康 |
| MinIO 日志归档 | `FORGEPILOT_MINIO_ENABLED=false`（`InMemoryObjectStorage`） | `true`，endpoint `http://localhost:9000`，账号见上 |
| 工作区保留宽限 | `FORGEPILOT_WORKSPACE_RETENTION_GRACE_DAYS=7` | 任务 `COMPLETED`/`FAILED`/`CANCELLED` 后超过宽限才删盘；**停止 Agent/验证容器不删工作区** |
| Docker 出口 | `FORGEPILOT_DOCKER_ALLOWLIST_NETWORK=false` → `network=none` | `true` 时使用自定义 bridge 名（默认 `forgepilot-egress`），需自备防火墙放行 Git/模型代理/包仓库 |

高频 Agent 日志只走 Redis Stream / SSE，**不会**进 RabbitMQ。归档失败会写 Outbox `LOG_ARCHIVE_RETRY`，且不删流中的块。


## 本机演示账号

Flyway `V3__local_demo_seed.sql` 写入下列账号，口令均为 `ForgePilot!dev`。`V4__demo_repositories.sql` 为支付项目补了两条仓库连接。

| 用户名 | 角色 | 说明 |
| --- | --- | --- |
| `demo_member` | 成员 | 演示科技「支付」项目成员，用于登录工作台 |
| `demo_admin` | 租户管理员 | 同一家公司的管理员，可新建项目 |
| `platform_admin` | 平台管理员 | 无租户，工作台默认空（不因管理身份跨项目） |
| `other_member` | 成员 | 另一家公司，用于租户隔离验收 |

## 已接通的主要接口

- `POST /api/v1/auth/login`、`GET /api/v1/me`
- `GET /api/v1/workbench?scope=waiting|mine|member`
- `GET|POST /api/v1/projects`，`GET /api/v1/projects/{id}`，`PATCH /api/v1/projects/{id}/defaults`
- `GET|POST /api/v1/projects/{id}/repositories`
- `GET|POST /api/v1/projects/{id}/requirements`
- `GET /api/v1/requirements/{id}`，`POST /api/v1/requirements/{id}/confirm-analysis`
- `GET|POST /api/v1/requirements/{id}/tasks`
- `GET /api/v1/tasks/{id}`，`POST /api/v1/tasks/{id}/cancel|abandon|approve`
- `POST /api/v1/tasks/{id}/verify`、`POST /api/v1/tasks/{id}/retry-delivery`（冻结仓检出 + 工作区根命令验证 + 有改动仓库推送 `fp/{taskId}` / Draft MR；不假装 Codex）
- `POST /api/v1/tasks/{id}/start|retry|input|request-changes`，`GET /api/v1/tasks/{id}/session`（Task ≠ Session；规约快照；执行侧 `/v1/responses` 代理）
- `GET /api/v1/nodes`，`POST /api/v1/internal/nodes/register|heartbeat|assign-next`；Session 日志 `GET .../sessions/{sid}/logs`（Redis Stream，失败回退内存；SSE 可选）
- `POST /api/v1/tasks/{taskId}/sessions/{sessionId}/archive-logs`，`GET .../archived-log` 与 `.../archived-log/meta`（MinIO/假存储指针）
- `GET|POST|PUT /api/v1/model-providers`（响应含 `hasSecret`，从不返回 apiKey）
- `GET|POST|PUT /api/v1/coding-tools`
- `GET|POST|PUT /api/v1/guidance`，`GET /api/v1/projects/{id}/conventions/effective`，`POST .../conventions/snapshots`
- `GET /api/v1/activity-logs`、`/activity-logs/export`、`/reports/daily`、`/reports/daily.md`（禁止改删）

创建任务时会把项目当前仓库集合与各仓库基线分支冻结到 `task_repository`。Task 不等于 Session，本阶段不创建 Agent Session。
Flyway `V5` 为演示科技写入了本地占位 Provider/Codex 工具和三层规约；密文是 `PLACEHOLDER_*`，不是生产密钥。

## 还没有的东西

本机无 `codex` 时 start 返回 `NOT_AVAILABLE` 并进入 `WAITING_USER`，不会把任务标为完成。长期密钥留在控制面密文库 / 执行侧代理内存，只把会话短时令牌给 Agent；Git 令牌经 `GIT_ASKPASS` 注入，不进 remote URL / 命令行。Docker daemon 非测试依赖（有 `RecordingFakeContainerRuntime`）；真实 Docker 实现可选。成员邀请/列表 API、完整终端态 `terminalAt` 驱动的保留清理、真实 egress bridge 防火墙编排仍待后续切片。

## 工作区保留

- **容器停止 ≠ 删除工作区**：`RetainingContainerStop` / `WorkspaceRetentionService.onContainerStopped` 只记日志。
- 仅当任务状态为 `COMPLETED` / `FAILED` / `CANCELLED`，且超过 `forgepilot.workspace.retention-grace-days`（默认 7）后，`cleanupEligible` 才删磁盘目录。
- MVP 用 `workspace.createdAt` 近似终态时间；生产应记录明确的 `terminalAt`。

## 隔离约定（MVP）

- Agent 不得接收控制面、PostgreSQL、Redis、RabbitMQ、MinIO 管理凭证。
- 模型入口只有会话级 Responses 代理 URL + 短时令牌；`wire_api = responses`。
- Docker 默认 `network=none`；`forgepilot.docker.allowlist-network.enabled=true` 时改用命名 allowlist bridge（仍须宿主侧放行 Git 主机、模型代理、包仓库）。
