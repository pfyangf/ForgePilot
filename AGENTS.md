# ForgePilot

本文件是编码 Agent 的约束清单，采用 [AGENTS.md](https://agents.md/) 的写法：短句、可执行、默认必须遵守。产品与架构的完整说明在 `docs/`，不要在根目录另写设计文档。

## 这是什么

ForgePilot（云端 AI Coding 平台）按公司租户隔离数据。用户创建项目并关联多个 Git 仓库，编写需求，确认 AI 分析草稿和任务拆分。平台在执行节点上为每个任务准备一个独立工作区目录，把项目里的每个仓库检出到稳定子目录中，在 Docker 沙箱中运行 Agent，再用另一个容器在同一工作区根目录执行项目配置的验证命令。通过后由平台按仓库推送同名分支 `fp/{taskId}`，只为有改动的仓库创建 Draft MR，最后由一次任务级人工审核收尾。控制面继续自建，不把别的 Coding Agent 产品分叉进本仓库。MVP 的 Agent 运行时是容器内的 Codex CLI。控制面负责任务状态、调度和交付；执行面只运行隔离环境中的 Agent 和验证命令。Task 不等于 Session。任务是否完成以平台证据和人工审核为准，不以 Agent 的自我报告为准。

## 文档

- 新的设计、产品、架构、决策文档一律放入 `docs/`，按类别分目录。
- 根目录只保留 `README.md` 和 `AGENTS.md`。不要在根目录新增方案、PRD 或 ADR。
- 现有类别：`docs/requirements/`（产品需求），`docs/architecture/`（架构），`docs/operations/`（部署与运维）。索引写在 `docs/README.md`。
- 需要新类别时（例如以后把 ADR 拆到 `docs/decisions/`），先建目录，再同时更新 `docs/README.md` 和本文件的目录地图。
- 改需求或架构时直接修改对应基线正文，保持前后一致。不要只在文末追加变更记录而留下旧结论。

目录地图：

- `docs/requirements/mvp-prd.md`：MVP 需求基线 V1.2。
- `docs/architecture/system-architecture.md`：架构基线 V1.2，含 ADR。
- `docs/operations/basic-environment.md`：小规格云主机上的基础环境部署说明。

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
11. 重要状态变更必须写入只追加的操作记录。记录可按租户查询，供审计和日报使用。不得写入密钥，普通用户不能改、不能删。工作台页面浏览不必逐条记录。
12. 所有执行操作必须有超时、取消和异常处理。
13. 不允许把 Agent 的 token、stdout 或详细工具日志写入 RabbitMQ。
14. 不允许在 Agent 容器内执行平台验证或测试。
15. 不允许以 Git Worktree 作为任务隔离的主方案。
16. 不允许长期密钥进入 Agent 容器。容器内只有会话级短时凭证。
17. 工作分支命名、`git push` 和 MR/PR 创建由平台完成。Agent 不得发明分支名。多仓库时，每个有改动的仓库使用同一个 `fp/{taskId}`，由平台分别推送并各创建一条 Draft MR。全部需要的 MR 齐了，任务才进入待审核。
18. 不允许控制面或 Web 通过 SSH 或挂载执行节点磁盘读取已完成产物。已完成的日志、Diff、报告和 bundle 从 MinIO 读取。
19. 不允许并发任务共享可写工作区、Agent 家目录、会话缓存、容器、容器网络或会话凭证。同一节点上的 bare mirror 只能只读共享。
20. 不允许跨租户读取另一家公司的项目、仓库、需求、任务、密钥、规约、文档或操作记录。平台级规约与文档对所有租户只读可见。
21. 任务启动后冻结仓库集合和各仓库的基线分支。不得用仓库排他锁阻止其他任务使用同一仓库。

## MVP 范围

要做的闭环：

创建项目 → 关联多个 Git 仓库 → 创建需求 → AI 分析草稿并经用户确认 → 拆分任务并经用户确认 → 为任务建立独立工作区并检出全部关联仓库 → 在 Docker 沙箱启动 Agent → 收集 Diff → 在新容器中按工作区根目录验证 → 失败则回灌（默认最多 2 次）或进入 `WAITING_USER` → 平台对有改动的每个仓库推送 `fp/{taskId}` 并创建 Draft MR → 一次任务级人工审核对照全部 MR 与证据 → 完成并写入交付记录。

MVP 要包含：

- 本地用户名/密码与 JWT。角色有平台管理员、租户管理员、项目管理员、成员。普通用户只属于一个租户。
- 租户是公司级数据隔离边界。项目是公司里的项目团队。MVP 不做跨租户共享，也不做计费、公开注册或租户域名产品。
- 个人工作台是登录后的首页，先按租户、再按项目成员收口。它只读项目、执行和交付状态，不是新的业务模块。
- 一个 Spring Boot 3 应用，包为 `system`、`project`、`execution`、`delivery`，外加独立部署的执行服务。
- 租户管理员维护本公司的编码工具目录和模型 Provider 目录。项目可设默认工具、Provider 和模型标识，有权限的成员可在任务上覆盖。
- 平台、公司、项目三层规约与文档。规约在任务启动时合成并冻结进会话。这不是文档库或知识库检索。
- 只追加的操作记录，以及据此生成日报的查询。不记录每次打开工作台。
- GitLab 适配器。领域模型保持与 Git 托管厂商无关，以便以后加 GitHub。
- 一个项目多条仓库连接。一次任务一个工作区目录，其中每个仓库一个检出。
- 可选的单个前置任务。不要做 DAG 调度器。
- Flyway 迁移，从第一天启用。

不要放进 MVP：

- 缺陷管理、API 文档管理、项目 Markdown 文档库、OpenAPI 导入或自动生成。
- Monaco Editor、xterm.js、在线终端、云 IDE、交互式 PTY。
- 把人工接管做成 IDE 会话，或在 Agent 未声明能力时假装进程已冻结可恢复。
- 六角色、五维权限编辑器、五种正式审批流。
- Token 消耗看板和资源配额产品。可以采集用量事件，但不要承诺准确的 Token 数字。
- 把多个 Git 远端合并成一个仓库历史。多仓库是多个检出和多条 Draft MR。
- 多 Agent 协作网络、自建 Git、自建 CI/CD 引擎、计费型公开 SaaS。
- 自研 Java 或 Python Agent 循环，以及把 OpenHands 或其他 Coding Agent 产品分叉进本仓库。
- 在边界稳定前拆成十几个 Maven 模块。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、WebSocket。Diff 用轻量 Diff 查看器。
- 控制面：Java 17、Spring Boot 3、MyBatis-Plus、Flyway。
- 数据与消息：PostgreSQL 为权威数据源；Redis 用于缓存、锁、心跳和高频流；RabbitMQ 只承载控制/状态事件；MinIO 存大文件与产物。
- 执行：Docker。MVP 的 Git 托管适配器是 GitLab。MVP 的 Agent 运行时是 Codex CLI。

在仓库根目录：

- 基础设施：`docker compose up -d`
- 测试：`mvn test`
- 控制面：`mvn -pl forgepilot-server spring-boot:run`（需要 PostgreSQL，监听 8080）
- 执行服务：`mvn -pl forgepilot-execution spring-boot:run`（监听 8081，不连接业务库）
- 前端依赖：`npm ci --prefix forgepilot-web`
- 前端类型检查：`npm run typecheck --prefix forgepilot-web`
- 前端构建：`npm run build --prefix forgepilot-web`
- 前端开发：`npm run dev --prefix forgepilot-web`（5173，把 `/api` 代理到控制面）

源级别是 Java 17。Compose 中的口令只用于本机开发。

## 后端布局

控制面是一个 Spring Boot 应用，先按包划分，不要提前拆 Maven 模块：

- `system`：用户、认证、角色、菜单权限、审计、模型 Provider 目录。
- `project`：项目、成员、多条仓库连接、需求与需求版本。需求不单独成顶层模块。
- `execution`：任务、Agent 配置与 Session、工作区、沙箱调度、证据与节点注册在控制面一侧的状态。
- `delivery`：验证编排、审核、按仓库的 MR、交付记录。

Agent Execution Service 是独立进程，负责镜像与克隆、容器、Agent 进程、模型代理、事件采集和产物上传。它可以位于同一仓库的另一个可运行应用中，但不得和控制面同进程。

模块边界稳定之前，不要把上述包拆成多个 Maven 业务模块。

## 工作区

- 每个仓库在执行节点上保留一个 bare mirror。mirror 可以按仓库只读共享，包括 `git clone --reference`。
- 每个任务有自己的工作区目录。启动时冻结当时的仓库集合，并把每个仓库从它自己的基线分支克隆到稳定子目录。之后项目增删仓库或改默认分支，不改变这个任务。可以用 `git clone --reference` 指向对应 mirror。
- 同一仓库可以被多个任务同时检出。不要加排他锁。各任务的平台分支仍是自己的 `fp/{taskId}`。
- 不要用 Git Worktree 做隔离。worktree 的 `.git` 文件记录宿主机绝对路径，容器内路径不一致就会失效；多个 worktree 共享对象库和锁，一个任务的 `git gc` 或锁争用会影响其他任务。
- 并发任务，包括不同需求下的任务，不得共享可写检出、Agent 家目录、Codex 或其他会话缓存。家目录放在该任务目录内。
- 同一执行节点可以同时跑多个任务，但隔离不因为同节点而放宽。调度仍遵守全局并发和单节点并发。
- 未提交、未推送的改动留在执行节点磁盘上。快照必须覆盖每一个检出，包括其中未提交和未跟踪的文件。
- 周期性，以及 Session 结束时，把每个检出的未提交和未跟踪改动记入该仓库自己的隐藏 ref 或打成 `git bundle`，存到 Git 远端和/或 MinIO。只打包其中一个检出或当前分支 HEAD 不够。
- 执行节点失效时，把该节点上的 Session 标为 `INTERRUPTED`，通知用户；新节点用快照恢复，或重新克隆再应用 bundle。
- 容器内非 root 用户的 UID/GID 必须与宿主机工作区属主一致，或采用等价的固定 UID 策略，否则 Agent 无法写文件。
- 容器挂载整个任务目录，不是只挂其中一个仓库子目录。
- 容器销毁不删除工作区。未结束任务的工作区不因保留期限被删。已结束且已有快照的工作区，才可以由保留策略清理。操作记录默认长期保留，只有平台保留作业可以删除过期记录。

## 沙箱、凭证与网络

- Agent 跑在非特权容器中。不要把宿主机 Docker Socket 挂进 Agent 容器。限制 CPU、内存、磁盘和时间。每个任务使用自己的 Agent 容器，验证时再起一个新容器。
- 每个 Agent 会话使用独立 Docker 网络，出站默认拒绝。允许列表要覆盖该项目全部仓库连接上的 Git 主机、本节点模型代理，以及项目需要的包仓库（npm、Maven 等）。
- Git：通过 `GIT_ASKPASS` 或 git credential helper 注入短时、限定到本次检出仓库的令牌。多台 Git 主机都要能匹配。不要把令牌放进环境变量或 remote URL。
- 模型：在执行节点上运行 sidecar / model proxy。执行服务按该次任务选定的 Provider 签发会话级 base URL 和短时令牌。Provider API Key 不得进入 Agent 容器，也不得出现在前端响应里。
- Codex CLI 只使用 Responses API（`wire_api = "responses"`）。Chat Completions 已移除。代理对 Agent 暴露的必须是 Responses 兼容接口。可以直接接 OpenAI、Azure OpenAI（自定义 Provider）、Amazon Bedrock、Google Cloud Vertex AI、Ollama、LM Studio，以及任何暴露 Responses 兼容 API 的网关（例如 OpenRouter）。
- 只提供原生 API 或 `/v1/chat/completions` 的 Provider（包括 Anthropic 原生接口，以及许多只支持 Chat Completions 的厂商）不能直接配给 Codex。由模型代理，或放在代理后面的翻译网关（例如 LiteLLM），在容器外翻译成 Responses。目录里要把这类 Provider 标成需要网关；网关未配置时，Codex 任务不得选用它。
- Agent 容器不得访问控制面、PostgreSQL、Redis、RabbitMQ、MinIO 管理接口或云元数据服务。
- Session 结束时撤销 Git 与模型的会话凭证。凭证按 Session 签发，不在并发任务之间复用。
- `git push` 和 MR/PR 由平台通过凭证代理执行，不由 Agent 进程执行。

## 验证与 MR

- Agent Session 结束后，在同一多仓库工作区的新容器中执行项目配置的构建/测试命令。命令的工作目录是工作区根目录。
- 验证容器不包含 Agent CLI，也不包含模型凭证。
- 这是项目配置的命令执行，不是自建 CI/CD 引擎。记录退出码、stdout/stderr；若现场有 JUnit XML 则保存。不要做通用测试报告解析产品。
- 审核界面必须标出任一仓库里测试文件是否被修改或删除。
- 自动回灌默认最多 2 次，可按项目配置。达到上限后任务进入 `WAITING_USER`，不要进入 `FAILED`。
- `FAILED` 只用于明确放弃，或不可恢复的基础设施错误。用户取消用 `CANCELLED`。节点死亡导致的中断进入 `WAITING_USER`，不要直接标失败。
- 验证通过后，由平台做统一交付：一个任务、一个工作区、各仓库同一个分支名 `fp/{taskId}`。有改动的每个仓库推送该分支并创建一条 Draft MR。没有改动的仓库不推送、不创建 MR。然后任务进入 `WAITING_APPROVAL`。
- 「统一合并分支」只表示上述含义。它不是把多个远端合并成一个 Git 历史，也不是跨远端做一次 merge。
- 创建 MR 必须幂等，范围是任务加仓库。同一仓库只保持一条开放 MR；head 前进时更新这条 MR。不同仓库各有自己的开放 MR。
- 部分仓库推送或创建 MR 失败时，已经成功的 MR 保留，任务进入 `WAITING_USER`，而不是 `FAILED`。重试只补未完成的仓库，仍用同一个分支名。
- 审核是任务级的一次决定，覆盖该任务的全部 Draft MR 和共享证据。审核通过把任务标为 `COMPLETED`，不要自动合并。要求修改时，在同一工作区上开新的 Session。

## 工作台

- 登录后的首页是个人工作台，不是新的业务模块，也不是第七个一级菜单之外的东西。它读取项目、执行和交付里已有的需求和任务。
- 访问边界先是租户，再是项目成员关系。用户只能看到本公司里自己所属项目的需求和任务。平台管理员不因为管理身份看到未加入的项目，也不能把一家公司的数据带给另一家。
- 默认筛选是「我的」：用户创建的需求，用户担任负责人的需求，用户创建或担任负责人的任务，以及「我的」需求下面的任务。另有「待我处理」：上述范围内待审核、等待人工，或会话正在等待输入的任务。「项目内全部」看到所属项目的全部条目。
- 从列表行打开详情。详情按状态展示不同内容和动作。
- 需求处于待分析或设计中时，展示可编辑的分析草稿和任务拆分，动作是编辑和确认。
- 任务执行中时，展示会话状态和普通文本日志。适配器声明了交互输入且会话处于 `WAITING_INPUT` 时，可以发一条消息。否则可以取消，或在同一工作区附说明后重试。未声明原生暂停时，不提供暂停后从原进程恢复。
- 验证中展示命令、日志和退出证据。待审核展示该任务的全部 Draft MR、轻量 Diff、证据，以及任一仓库中被修改或删除的测试文件。通过则完成且不合并；要求修改则在同一工作区开新 Session。
- 等待人工、中断、失败、完成和取消只展示已有证据和基线里已经规定的动作。
- 不引入 Monaco、xterm.js、PTY 或云 IDE。

## 规约与文档

- 三层：平台管理员维护平台层，租户管理员维护公司层，项目管理员维护项目层。更具体的一层在同标题上覆盖上一层，标题不同的规则都生效。
- 规约默认进入 Agent。文档默认只给人看，勾选后才进入 Agent。
- 任务启动时把合成结果快照进 Session。会话进行中再改文档，不改变这次 Agent。
- 元数据在 PostgreSQL，大文件在 MinIO。公司层和项目层不跨租户。平台层所有租户可读。
- 不要做成通用文档库、评论或知识库检索。

## 操作记录

- 登录与鉴权失败、租户和项目变更、规约与文档、Provider 配置、需求、确认、任务、会话、验证、推送、MR 和审核都要留下只追加记录。
- 每条记录有租户、操作者、时间、动作、对象、可选项目、请求号、中文摘要、结构化载荷和成败。平台级操作的租户为空。
- 公司只能查自己的记录。平台管理员可以跨租户查询。项目成员只能看自己有权看到的项目记录。载荷里的令牌、密码和密钥必须打码。
- 记录要能按租户、项目、操作者、时间、动作、需求和任务筛选，并生成日报：当天每人、每个项目新建、推进、完成、审核和受阻的数量，再加上仍待处理的存量。
- MVP 提供查询和导出，不做好完整报表产品，也不把定时邮件当成验收项。

## 事件流

- 控制/状态事件（任务状态、Session 生命周期、审核、MR 已创建）走 PostgreSQL Outbox，再由 RabbitMQ 投递。
- Agent stdout、token、详细工具日志走 Redis Stream，或由执行服务经控制面代理的 WebSocket。完整日志异步写入 MinIO。
- PostgreSQL 的 `agent_event` 只存重要事件：状态变化、工具调用摘要、文件变更记录。不要把每个 token 写入 PostgreSQL。
- 前端 WebSocket 按序号续传。
- 容量不足时，任务排在控制面的 PostgreSQL 队列里。不要只把积压放在 RabbitMQ。
- 执行节点向控制面注册（`nodeId`、容量、标签）并心跳。调度器遵守全局并发和单节点并发；按项目的并发上限以后再做。同节点上的多个任务仍然各自隔离。
- 执行服务向控制面推送事件、心跳、结果和产物；控制面调用执行节点以开始或取消工作。面向执行面的内部 API 使用独立服务身份，不要复用用户 JWT。

## 控制面与 Agent 的边界

- 控制面是任务状态的唯一权威。
- Agent 可以提交日志、Diff 和求助，不能把任务标成完成，不能写平台库。
- AI 需求分析是可编辑草稿，依据是需求文本加上可选的文件树或 README。不要把它实现成可靠的代码库级影响分析，不要自动写入正式需求。
- 需求状态允许人工修改。最小自动规则：任一任务处于执行相关状态则需求为「开发中」；未取消任务全部完成则需求为「待验收」。不要让八个需求状态和九个任务状态各写各的。
- MVP 的两个人工检查点是：用户确认分析与任务拆分（这是确认，不是正式审批流）；验证并为有改动的仓库创建 Draft MR 之后的一次代码审核。执行前审批和高风险操作审批只留后续扩展的策略挂钩，不要做成 MVP 流程。
- `WAITING_INPUT` 时，仅当适配器声明交互输入，才允许用户发送一条消息。除此以外，人工接管就是取消，或在同一工作区附说明后重试。Agent 未声明原生暂停时，不要提供暂停/恢复。

## 如何扩展

继续建设 ForgePilot 自己的控制面。不要把 OpenHands 或其他 Coding Agent 产品分叉进本仓库。MVP 不实现自研的 Java 或 Python Agent 循环。

新增能力时实现对应端口，不要在领域服务里分支判断厂商：

- Agent Adapter：声明是否支持交互输入、原生暂停、中断、事件流、工具事件、会话恢复和结构化结果。平台按声明的能力提供操作。第一个适配器是 OpenAI Codex CLI，由平台准备多仓库工作区和沙箱，CLI 在 Agent 容器内运行。Codex 的 MVP 声明不含原生暂停，也不把消息送进仍在运行的进程。`git push` 和 Draft MR 仍由平台在验证通过后执行。
- 下一个适配器只留端口：OpenHands Agent Server（REST/WebSocket），以及 ACP 兼容 Agent。
- Sandbox Provider：MVP 只实现 Docker。
- Git/Integration Adapter：仓库、分支、Diff、MR/PR。MVP 实现 GitLab，接口保持可增加 GitHub。一次交付会对每个有改动的仓库各调用一次。
- Model Provider：租户管理员维护本公司目录，记录名称、类型、base URL、密文引用、是否启用、API 版本和上游协议。平台自身调用与 Agent 容器内调用分开。Agent 侧只拿到选定 Provider 的会话级模型代理，线路是 Responses。密钥不写入操作记录。

执行面内部契约保持稳定：注册、心跳、启动、取消、事件、结果、产物。业务规则留在控制面。

## 本文件与详细文档

实现时同时遵守本文件和 `docs/` 中的 V1.2 基线。本文件是绑定检查清单；行为细节、状态迁移、数据实体和验收标准以 `docs/requirements/mvp-prd.md` 与 `docs/architecture/system-architecture.md` 为准。发现二者冲突时，先改文档使之一致，再写代码。
