# ForgePilot

ForgePilot（云端 AI Coding 平台）以需求为入口，调度 AI Agent 在隔离环境中修改代码，再由平台独立验证、人工审核后完成交付。平台负责编排、证据和交付；Agent 只负责授权范围内的读写和命令执行。

本仓库当前是设计基线，尚不包含应用代码。

## 文档

产品、架构和后续设计文档都在 [`docs/`](docs/README.md)。除本文件和 [`AGENTS.md`](AGENTS.md) 外，不要在仓库根目录放置独立设计文档。

- [文档索引与分类规则](docs/README.md)
- [MVP 需求规格说明书 V1.1](docs/requirements/mvp-prd.md)
- [系统基础架构设计说明书 V1.1](docs/architecture/system-architecture.md)

## 编码 Agent

实现前先阅读 [`AGENTS.md`](AGENTS.md)。该文件是必须遵守的约束清单；需求与架构的完整说明在 `docs/`。
