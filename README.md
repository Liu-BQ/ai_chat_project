# AI Chat Project

git 提交习惯

| 前缀      | 中文含义   | 使用场景                             | 示例                                      |
|-----------|------------|--------------------------------------|-------------------------------------------|
| `feat:`   | 新功能     | 新增功能、模块、接口                 | `feat: 添加用户登录接口`                  |
| `fix:`    | 修复 bug   | 修复缺陷、异常、错误逻辑             | `fix: 修复聊天消息无法发送的问题`         |
| `docs:`   | 文档       | 更新 README、注释、文档              | `docs: 补充 Ollama 配置说明`              |
| `style:`  | 代码格式   | 空格、分号、格式化（不影响逻辑）     | `style: 格式化 ChatService 代码`          |
| `refactor:` | 重构     | 代码优化、结构调整（非功能/修复）    | `refactor: 拆分 AI 调用逻辑到独立 service`|
| `perf:`   | 性能优化   | 提升性能、减少资源消耗               | `perf: 优化数据库查询，减少 N+1 问题`     |
| `test:`   | 测试       | 添加/修改测试用例                    | `test: 为 ChatController 添加单元测试`    |
| `build:`  | 构建       | 依赖、工具链、CI/CD 相关             | `build: 升级 Spring Boot 到 4.0.1`        |
| `ci:`     | 持续集成   | GitHub Actions、Jenkins 等配置       | `ci: 添加 Maven 缓存加速构建`             |
| `chore:`  | 杂项       | 其他琐碎任务（不涉及代码逻辑）       | `chore: 删除无用日志打印`                 |
| `revert:` | 回滚       | 撤销某次提交                         | `revert: feat: 临时回退新聊天界面`        |


一个基于 **Spring Boot 4 + Vue 3 + MySQL 8** 的前后端分离 AI 聊天应用，通过调用本地 [Ollama](https://ollama.com/) 运行的 Qwen 等大语言模型，实现 Web 页面与 AI 的实时交互。

> 🌟 支持对话历史存储、响应流式输出（可选）、用户友好界面，适用于本地私有化部署 AI 助手。

---

## 🛠 技术栈

| 类别       | 技术/框架 |
|------------|----------|
| 后端       | Spring Boot 4.0.1, Java 21, Spring WebFlux (响应式), Spring Data JPA |
| 前端       | Vue 3 (Composition API), Axios, Tailwind CSS / Element Plus（可选）|
| 数据库     | MySQL 8 |
| AI 引擎    | [Ollama](https://ollama.com/)（本地运行 Qwen、Llama3 等模型）|
| 构建工具   | Maven, Vite |
| 其他       | Lombok, MapStruct, Jakarta Validation, Spring Boot Actuator |

---

## 📦 环境要求

- **Java 21**（必须）
- **Maven 3.8+**
- **MySQL 8.0+**
- **Node.js 18+**（用于前端开发）
- **Ollama 已安装并运行**（[官网下载](https://ollama.com/download)）

> 💡 推荐在 Windows / macOS / Linux 上使用 **Docker** 或 **本地直接运行** Ollama。

---

## ⚙️ 快速启动

### 1. 启动 Ollama 并加载模型

```bash
# 安装 Ollama 后，在终端运行：
ollama run qwen:latest
# 或其他支持的模型，如 llama3, gemma 等