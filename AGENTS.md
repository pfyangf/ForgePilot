# ForgePilot

本文件是编码 Agent 的约束清单，采用 [AGENTS.md](https://agents.md/) 的写法：短句、可执行、默认必须遵守。产品与架构的完整说明在 `docs/`，不要在根目录另写设计文档。

## 这是什么

ForgePilot（云端 AI Coding 平台）是单组织部署的云端 AI Coding 平台。用户创建项目并关联一个 Git 仓库，编写需求，确认 AI 分析草稿和任务拆分；平台在执行节点上为任务准备独立代码工作区，在 Docker 沙箱中运行 Agent，再用另一个容器执行项目配置的验证命令，通过后由平台推送分支、创建 Draft MR，最后由人工审核收尾。控制面负责任务状态、调度和交付；执行面只运行隔离环境中的 Agent 和验证命令。Task 不等于 Session。任务是否完成以平台证据和人工审核为准，不以 Agent 的自我报告为准。

## 文档

- 新的设计、产品、架构、决策文档一律放入 `docs/`，按类别分目录。
- 根目录只保留 `README.md` 和 `AGENTS.md`。不要在根目录新增方案、PRD 或 ADR。
- 现有类别：`docs/requirements/`（产品需求），`docs/architecture/`（架构）。索引写在 `docs/README.md`。
- 需要新类别时（例如以后把 ADR 拆到 `docs/decisions/`），先建目录，再同时更新 `docs/README.md` 和本文件的目录地图。
- 改需求或架构时直接修改对应基线正文，保持前后一致。不要只在文末追加变更记录而留下旧结论。

目录地图：

- `docs/requirements/mvp-prd.md`：MVP 需求基线 V1.1。
- `docs/architecture/system-architecture.md`：架构基线 V1.1，含 ADR。

## 架构硬约束

下列条款与 `docs/architecture/system-architecture.md` 第 15 章一致。长期密钥包括 Git 平台令牌、模型 Provider API Key、数据库口令、消息队列口令和对象存储管理密钥。

1. 不允许业务控制面直接运行 Agent 进程。
2. 不允许 Agent 直接修改平台业务数据库。
3. 不允许 Agent 自行修改任务最终状态。任务状态只由控制面根据规则变更。
4. 不允许把 Agent Session 和 Task 合并为一个实体。一个 Task 可以有多个历史 Session，同一时刻默认只有一个活动 Session。
5. 不允许将长期密钥暴露给 Agent。
6. 不允许使用消息队列作为业务状态的唯一存储。权威状态在 PostgreSQL。
7. 不允许容器销毁时无条件删除工作区。
8. 不允许跳过平台验证直接认定代码可交付。
9. 不允许 Agent 合并受保护分支。MVP 不自动合并。
10. 不允许绕过适配器接入新的 Agent、Sandbox、Git/Integration 或 Model Provider，也不允许厂商类型进入领域模型。
11. 重要状态变更必须有持久化事件和审计记录。
12. 所有执行操作必须有超时、取消和异常处理。
13. 不允许把 Agent 的 token、stdout 或详细工具日志写入 RabbitMQ。
14. 不允许在 Agent 容器内执行平台验证或测试。
15. 不允许以 Git Worktree 作为任务隔离的主方案。
16. 不允许长期密钥进入 Agent 容器。容器内只有会话级短时凭证。
17. 工作分支命名、`git push` 和 MR/PR 创建由平台完成。Agent 不得发明分支名。
18. 不允许控制面或 Web 通过 SSH 或挂载执行节点磁盘读取已完成产物。已完成的日志、Diff、报告和 bundle 从 MinIO 读取。

## MVP 范围

要做的闭环：

创建项目 → 关联一个 Git 仓库 → 创建需求 → AI 分析草稿并经用户确认 → 拆分任务并经用户确认 → 从 bare mirror 克隆工作区 → 在 Docker 沙箱启动 Agent → 收集 Diff → 在新容器中验证 → 失败则回灌（默认最多 2 次）或进入 `WAITING_USER` → 平台推送 `fp/{taskId}` 并创建 Draft MR → 人工对照 MR 与证据审核 → 完成并写入交付记录。

MVP 要包含：

- 本地用户名/密码与 JWT。角色只有平台管理员、项目管理员、成员。权限是菜单权限加项目成员关系。
- 单组织。项目是隔离边界。
- 一个 Spring Boot 3 应用，包为 `system`、`project`、`execution`、`delivery`，外加独立部署的执行服务。
- GitLab 适配器。领域模型保持与 Git 托管厂商无关，以便以后加 GitHub。
- 可选的单个前置任务。不要做 DAG 调度器。
- Flyway 迁移，从第一天启用。

不要放进 MVP：

- 缺陷管理、API 文档管理、项目 Markdown 文档库、OpenAPI 导入或自动生成。
- Monaco Editor、xterm.js、在线终端、云 IDE、交互式 PTY。
- 把人工接管做成 IDE 会话，或在 Agent 未声明能力时假装进程已冻结可恢复。
- 六角色、五维权限编辑器、五种正式审批流。
- Token 消耗看板和资源配额产品。可以采集用量事件，但不要承诺准确的 Token 数字。
- 单项目多仓库、多 Agent 协作网络、自建 Git、自建 CI/CD 引擎、多租户 SaaS。
- 在边界稳定前拆成十几个 Maven 模块。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、WebSocket。Diff 用轻量 Diff 查看器。
- 控制面：Java 17、Spring Boot 3、MyBatis-Plus、Flyway。
- 数据与消息：PostgreSQL 为权威数据源；Redis 用于缓存、锁、心跳和高频流；RabbitMQ 只承载控制/状态事件；MinIO 存大文件与产物。
- 执行：Docker。MVP 的 Git 托管适配器是 GitLab。

当前仓库没有应用代码。开始实现后，把构建、运行和测试命令补进本节，不要只写在对话或零散笔记里。

## 后端布局

控制面是一个 Spring Boot 应用，先按包划分，不要提前拆 Maven 模块：

- `system`：用户、认证、角色、菜单权限、审计。
- `project`：项目、成员、仓库连接、需求与需求版本。需求不单独成顶层模块。
- `execution`：任务、Agent 配置与 Session、工作区、沙箱调度、证据与节点注册在控制面一侧的状态。
- `delivery`：验证编排、审核、MR、交付记录。

Agent Execution Service 是独立进程，负责镜像与克隆、容器、Agent 进程、模型代理、事件采集和产物上传。它可以位于同一仓库的另一个可运行应用中，但不得和控制面同进程。

模块边界稳定之前，不要把上述包拆成多个 Maven 业务模块。

## 工作区

- 每个仓库在执行节点上保留一个 bare mirror。
- 每个任务单独 `git clone`。可以用 `git clone --reference` 指向 mirror 以节省磁盘。
- 不要用 Git Worktree 做隔离。worktree 的 `.git` 文件记录宿主机绝对路径，容器内路径不一致就会失效；多个 worktree 共享对象库和锁，一个任务的 `git gc` 或锁争用会影响其他任务。
- 未提交、未推送的改动留在执行节点磁盘上。
- 周期性，以及 Session 结束时，把未提交和未跟踪的改动记入隐藏 ref 或打成 `git bundle`，存到 Git 远端和/或 MinIO。只打包当前分支 HEAD 不够。
- 执行节点失效时，把该节点上的 Session 标为 `INTERRUPTED`，通知用户；新节点用快照恢复，或重新克隆再应用 bundle。
- 容器内非 root 用户的 UID/GID 必须与宿主机工作区属主一致，或采用等价的固定 UID 策略，否则 Agent 无法写文件。
- MVP 每个项目只关联一个主仓库。

## 沙箱、凭证与网络

- Agent 跑在非特权容器中。不要把宿主机 Docker Socket 挂进 Agent 容器。限制 CPU、内存、磁盘和时间。
- Git：通过 `GIT_ASKPASS` 或 git credential helper 注入短时、项目范围的令牌。不要把令牌放进环境变量或 remote URL。
- 模型：在执行节点上运行 sidecar / model proxy。Agent 容器只拿到会话级 base URL 和短时令牌。Provider API Key 不得进入 Agent 容器。
- Agent 容器使用独立 Docker 网络，出站默认拒绝。只允许 Git 主机、模型代理和项目需要的包仓库（npm、Maven 等）。
- Agent 容器不得访问控制面、PostgreSQL、Redis、RabbitMQ、MinIO 管理接口或云元数据服务。
- Session 结束时撤销 Git 与模型的会话凭证。
- `git push` 和 MR/PR 由平台通过凭证代理执行，不由 Agent 进程执行。

## 验证与 MR

- Agent Session 结束后，在同一工作区的新容器中执行项目配置的构建/测试命令。
- 验证容器不包含 Agent CLI，也不包含模型凭证。
- 这是项目配置的命令执行，不是自建 CI/CD 引擎。记录退出码、stdout/stderr；若现场有 JUnit XML 则保存。不要做通用测试报告解析产品。
- 审核界面必须标出测试文件是否被修改或删除。
- 自动回灌默认最多 2 次，可按项目配置。达到上限后任务进入 `WAITING_USER`，不要进入 `FAILED`。
- `FAILED` 只用于明确放弃，或不可恢复的基础设施错误。用户取消用 `CANCELLED`。节点死亡导致的中断进入 `WAITING_USER`，不要直接标失败。
- 验证通过后，由平台推送 `fp/{taskId}`（或同等平台拥有的分支名）并创建 Draft MR，然后任务进入 `WAITING_APPROVAL`。
- 创建 MR 必须幂等，以任务加 head SHA 或等价键去重。同一任务保持一个平台分支和一条开放 MR；head 前进时更新这条 MR，不要另开一条。
- 审核通过把任务标为 `COMPLETED`，不要自动合并。要求修改时，在同一工作区上开新的 Session。

## 事件流

- 控制/状态事件（任务状态、Session 生命周期、审核、MR 已创建）走 PostgreSQL Outbox，再由 RabbitMQ 投递。
- Agent stdout、token、详细工具日志走 Redis Stream，或由执行服务经控制面代理的 WebSocket。完整日志异步写入 MinIO。
- PostgreSQL 的 `agent_event` 只存重要事件：状态变化、工具调用摘要、文件变更记录。不要把每个 token 写入 PostgreSQL。
- 前端 WebSocket 按序号续传。
- 容量不足时，任务排在控制面的 PostgreSQL 队列里。不要只把积压放在 RabbitMQ。
- 执行节点向控制面注册（`nodeId`、容量、标签）并心跳。调度器遵守全局并发和单节点并发；按项目的并发上限以后再做。
- 执行服务向控制面推送事件、心跳、结果和产物；控制面调用执行节点以开始或取消工作。面向执行面的内部 API 使用独立服务身份，不要复用用户 JWT。

## 控制面与 Agent 的边界

- 控制面是任务状态的唯一权威。
- Agent 可以提交日志、Diff 和求助，不能把任务标成完成，不能写平台库。
- AI 需求分析是可编辑草稿，依据是需求文本加上可选的文件树或 README。不要把它实现成可靠的代码库级影响分析，不要自动写入正式需求。
- 需求状态允许人工修改。最小自动规则：任一任务处于执行相关状态则需求为「开发中」；未取消任务全部完成则需求为「待验收」。不要让八个需求状态和九个任务状态各写各的。
- MVP 的两个人工检查点是：用户确认分析与任务拆分（这是确认，不是正式审批流）；验证并创建 Draft MR 之后的代码审核。执行前审批和高风险操作审批只留后续扩展的策略挂钩，不要做成 MVP 流程。
- `WAITING_INPUT` 时允许用户发送一条消息。除此以外，人工接管就是取消，或在同一工作区附说明后重试。Agent 未声明原生暂停能力时，不要提供暂停/恢复。

## 如何扩展

新增能力时实现对应端口，不要在领域服务里分支判断厂商：

- Agent Adapter：声明是否支持交互输入、原生暂停、中断、事件流、工具事件、会话恢复和结构化结果。平台按声明的能力提供操作。
- Sandbox Provider：MVP 只实现 Docker。
- Git/Integration Adapter：仓库、分支、Diff、MR/PR。MVP 实现 GitLab，接口保持可增加 GitHub。
- Model Provider：平台自身调用与 Agent 容器内调用分开。Agent 侧只指向会话级模型代理。

执行面内部契约保持稳定：注册、心跳、启动、取消、事件、结果、产物。业务规则留在控制面。

## 本文件与详细文档

实现时同时遵守本文件和 `docs/` 中的 V1.1 基线。本文件是绑定检查清单；行为细节、状态迁移、数据实体和验收标准以 `docs/requirements/mvp-prd.md` 与 `docs/architecture/system-architecture.md` 为准。发现二者冲突时，先改文档使之一致，再写代码。
