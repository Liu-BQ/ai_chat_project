# AI Chat Project - 架构设计文档

> 一个基于 **Spring Boot 4 + Java 21** 的模块化、响应式 AI 聊天应用，通过本地 Ollama 服务调用大语言模型（如 Qwen），支持对话历史存储与私有化部署。

---

## 🧱 整体架构

本项目采用 **分层模块化架构**，将不同关注点分离到独立 Maven 模块中，实现高内聚、低耦合。整体分为四层：

```mermaid
ai-chat-project (Root POM)
├── model-app             # 应用启动入口
├── model-web-api         # Web 控制器 & DTO & 业务编排
├── model-ollama-client   # Ollama AI 引擎客户端
└── model-persistence     # 数据持久化层

