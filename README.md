# ForgePilot

ForgePilot（云端 AI Coding 平台）以需求为入口，调度 AI Agent 在隔离环境中修改代码，再由平台独立验证、人工审核后完成交付。平台负责编排、证据和交付；Agent 只负责授权范围内的读写和命令执行。

一个项目关联多个 Git 仓库。一次任务使用一个工作区目录，平台把这些仓库都检出到该目录里。MVP 的 Agent 运行时是容器内的 Codex CLI。模型长期密钥留在执行节点的代理侧。

当前仓库是 V1.2 基线上的工程骨架：控制面、执行服务和前端可以编译，端口和页面是占位实现，还没有打通业务闭环。

## 文档

产品、架构和后续设计文档都在 [`docs/`](docs/README.md)。除本文件和 [`AGENTS.md`](AGENTS.md) 外，不要在仓库根目录放置独立设计文档。

- [文档索引与分类规则](docs/README.md)
- [MVP 需求规格说明书 V1.2](docs/requirements/mvp-prd.md)
- [系统基础架构设计说明书 V1.2](docs/architecture/system-architecture.md)

## 仓库结构

- `forgepilot-server/`：Spring Boot 3 控制面，包为 `system`、`project`、`execution`、`delivery`。
- `forgepilot-execution/`：独立进程的执行服务。
- `forgepilot-web/`：Vue 3 工作台骨架。
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

## 骨架里还没有的东西

骨架不启动 Codex，不调用 GitLab，不创建容器，也不保管真实的 Provider 密钥。工作台页面使用浏览器内的示例数据，用来展示个人列表和按状态区分的详情。
