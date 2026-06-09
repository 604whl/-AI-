# XhsAgent 小红书运营 AI Agent

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-blue)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> 基于 Spring Boot 3.5.14 + Spring AI 的小红书全平台内容运营 AI Agent，覆盖美妆、穿搭、美食、旅行等品类，提供笔记分析评分、标题生成、封面诊断、合规检测等智能化内容优化能力。

---

## 目录

- [项目介绍](#项目介绍)
- [技术栈](#技术栈)
- [已实现功能](#已实现功能)
- [规划中功能](#规划中功能)
- [部署与使用指南](#部署与使用指南)
- [项目结构](#项目结构)
- [API 文档](#api-文档)
- [贡献指南](#贡献指南)

---

## 项目介绍

XhsAgent 是一个面向小红书内容创作者的 AI 运营助手，支持**全平台全品类**内容分析。通过大语言模型对笔记内容进行多维度分析评分，帮助创作者提升内容质量和互动数据。

核心价值：
- **智能分析** — 从 CTR、情绪共鸣、收藏价值、转化引导、传播潜力五个维度对笔记进行 0-100 量化评分
- **内容优化** — 基于分析结果生成优化建议和改写方案
- **标题生成** — AI 批量生成高 CTR 标题变体，标注文案技巧
- **封面诊断** — 多模态视觉模型分析封面图的视觉质量与点击吸引力
- **合规检测** — 自动识别绝对化用语、虚假背书等违规风险

---

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 21 |
| 后端框架 | Spring Boot | 3.5.14 |
| AI 框架 | Spring AI + Spring AI Alibaba | 1.1.2 |
| LLM 服务 | 阿里云 DashScope（通义千问） | 2.19.1 |
| 数据库 | PostgreSQL + pgvector | - |
| ORM | MyBatis-Plus | 3.5.9 |
| 缓存 | Redis | - |
| 对象存储 | MinIO | - |
| 数据库迁移 | Flyway | - |
| 认证 | JWT（JJWT） | 0.12.6 |
| API 文档 | Knife4j（OpenAPI 3） | 4.4.0 |
| 工具库 | Hutool | 5.8.38 |

---

## 已实现功能

- [x] **用户认证体系** — 邮箱注册/登录，JWT 双 Token 机制（Access + Refresh），Redis 存储刷新令牌
- [x] **笔记内容分析** — 异步分析流水线，LLM 生成结构化 JSON 评分报告（五维评分、内容分类、结构分析、问题诊断）
- [x] **封面图视觉分析** — 基于 Qwen-VL 多模态模型的封面质量评估
- [x] **内容优化改写** — 指定语气和长度，AI 生成优化版本
- [x] **标题批量生成** — 生成 5-10 个标题变体，附带 CTR 预估和技巧标注
- [x] **合规风险扫描** — 基于规则引擎的内容合规检测
- [x] **用户配额管理** — 每日分析次数限制，用量追踪
- [x] **文件上传** — 封面图上传（支持 MinIO / 本地文件系统），格式与大小校验
- [x] **Mock 模式** — 离线开发模式，无需 API Key 即可调试全流程
- [x] **RAG 架构** — pgvector 向量检索基础设施已就绪（知识库 Schema + 检索接口）

---

## 规划中功能

- [ ] **TODO: 知识库集成** — 接入爆款案例库，通过 RAG 为分析注入行业参考数据，提升评分准确性
- [ ] **TODO: 工具调用与联网能力** — Agent 具备搜索引擎调用、热点话题抓取、竞品数据查询等外部工具能力
- [x] **MCP（Model Context Protocol）支持** — Streamable HTTP MCP Server，见 [Xhs-Backend/docs/MCP-SERVER.md](./Xhs-Backend/docs/MCP-SERVER.md)

---

## 部署与使用指南

### 环境要求

- JDK 21+
- PostgreSQL 15+（需启用 pgvector 扩展）
- Redis 7+
- MinIO（可选，不配置则使用本地文件存储）
- Maven 3.9+

### 基础设施启动

项目提供 Docker Compose 一键启动依赖服务：

```bash
cd infra
docker-compose up -d
```

这将启动 PostgreSQL（含 pgvector）、Redis 和 MinIO。

### 配置说明

核心配置通过环境变量注入，主要配置项：

```yaml
# AI 服务配置
DASHSCOPE_API_KEY=sk-xxx          # 阿里云 DashScope API Key（必填）
AI_MOCK_ENABLED=false             # 设为 true 可跳过真实 AI 调用

# 数据库
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/xhs_agent
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# MinIO（可选）
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
```

完整配置参见 `src/main/resources/application.yml`。

### 构建与启动

```bash
# 构建项目
mvn clean package -DskipTests

# 启动应用
java -jar target/xhs-agent-yunying-0.0.1-SNAPSHOT.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

应用启动后访问：
- API 基础路径：`http://localhost:8125/api`
- Swagger 文档：`http://localhost:8125/api/swagger-ui.html`

---

## 项目结构

```
Xhs-Backend/
├── src/main/java/com/shortvideoscripagent/xhsagentyunying/
│   ├── ai/                          # AI 核心模块
│   │   ├── cover/                   # 封面视觉分析
│   │   ├── model/                   # LLM Provider 抽象层
│   │   ├── orchestrator/            # 分析编排器（异步流水线）
│   │   ├── parser/                  # LLM 输出解析
│   │   ├── prompt/                  # Prompt 工程
│   │   └── rag/                     # RAG 检索模块
│   ├── auth/                        # 认证授权
│   ├── common/                      # 通用组件（统一响应、异常处理）
│   ├── config/                      # 配置类
│   ├── controller/v1/               # REST API 控制器
│   ├── domain/                      # 领域模型（实体、Mapper）
│   ├── dto/                         # 数据传输对象
│   └── service/                     # 业务服务层
├── src/main/resources/
│   ├── application.yml              # 应用配置
│   ├── db/migration/                # Flyway 数据库迁移脚本
│   └── prompts/                     # Prompt 模板文件
├── infra/                           # 基础设施（Docker Compose）
├── docs/                            # 项目文档
└── pom.xml                          # Maven 依赖管理
```

---

## API 文档

启动应用后访问 Knife4j 文档界面获取完整 API 说明：

```
http://localhost:8125/api/swagger-ui.html
```

主要接口：

| 模块 | 端点 | 说明 |
|------|------|------|
| 认证 | `POST /v1/auth/register` | 用户注册 |
| 认证 | `POST /v1/auth/login` | 用户登录 |
| 分析 | `POST /v1/analysis/submit` | 提交笔记分析 |
| 分析 | `GET /v1/analysis/{taskId}` | 查询分析结果 |
| 标题 | `POST /v1/titles` | 生成标题变体 |
| 合规 | `POST /v1/compliance/scan` | 合规检测 |
| 文件 | `POST /v1/files/cover` | 上传封面图 |

---

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/your-feature`)
3. 提交更改 (`git commit -m 'feat: add some feature'`)
4. 推送到分支 (`git push origin feature/your-feature`)
5. 创建 Pull Request

---

## License

[MIT](LICENSE)
