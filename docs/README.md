# ForgePilot 文档索引

本目录是 ForgePilot 的设计与产品文档根目录。当前基线版本为 **V1.3**（在 V1.2 上的约定扩展）。

## 分类规则

后续所有设计、产品、架构和决策文档都放在 `docs/` 下，按类别分目录存放。

仓库根目录只保留以下两类入口文件：

- `README.md`：项目简介与构建运行命令。
- `AGENTS.md`：编码 Agent 的约束清单。

不要在仓库根目录新增独立的需求说明、架构说明、ADR 或方案草稿。此类文件一律进入 `docs/` 的对应类别。

新增类别时：

1. 在 `docs/` 下建立含义明确的英文目录名，例如日后把 ADR 从架构正文拆出时使用 `docs/decisions/`。
2. 在该目录放置文档，并在本索引中增加链接和一句话说明。
3. 更新 [`AGENTS.md`](../AGENTS.md) 中的目录地图，使两处一致。

不要把新文档堆进已有目录凑合归类。类别表达的是文档用途，不是作者或日期。

## 当前类别

| 目录 | 用途 | 当前文档 |
| --- | --- | --- |
| [`docs/requirements/`](requirements/) | 产品需求 | [MVP 需求规格说明书 V1.3](requirements/mvp-prd.md)、[需求研发全流程（轻量）](requirements/requirement-lifecycle.md) |
| [`docs/architecture/`](architecture/) | 架构设计 | [系统基础架构设计说明书 V1.3](architecture/system-architecture.md) |
| [`docs/operations/`](operations/) | 部署与运维 | [基础环境部署说明](operations/basic-environment.md)、[控制面 JAR 部署注意](operations/control-plane-jar-deploy.md) |
| [`docs/frontend/`](frontend/) | 前端实现约定 | [样式规范](frontend/ui-style.md) |

架构说明书中的 ADR 表是当前的决策记录。尚未单独设立 `docs/decisions/`。将来若把单条 ADR 拆成独立文件，再新增该目录并回写本索引。

## 阅读顺序

1. 仓库根目录 [`AGENTS.md`](../AGENTS.md)：实现时不能违反的硬约束。
2. [MVP 需求规格说明书](requirements/mvp-prd.md)：做什么、不做什么、如何验收。
3. [需求研发全流程（轻量）](requirements/requirement-lifecycle.md)：需求评审、设计稿、缺陷、验收和发布的门禁与环节。
4. [系统基础架构设计说明书](architecture/system-architecture.md)：控制面、执行面、工作区、凭证、事件和部署如何落地。
5. [基础环境部署说明](operations/basic-environment.md)：在小规格云主机上做功能验收的精简部署步骤。覆盖运行中 jar 前先读 [控制面 JAR 部署注意](operations/control-plane-jar-deploy.md)。
6. [前端样式规范](frontend/ui-style.md)：工作台视觉与 Element Plus 的用法。

V1.3 正文（含流程说明）是当前基线。V1.1 里的单组织、单仓库、三种角色和「不做多租户」已经撤回。若与更早的口头结论或 V1.2 中「不建缺陷/文档/发布」的旧表述冲突，以 V1.3 文档和 `AGENTS.md` 为准。
