# 小哈 AI 机器人（后端学习指南）

面向读者：**已有 Spring 经验，但未使用过 Spring AI / RAG**。目标是用最短时间看懂本项目后端的“入口 → 链路 → 可改的业务点”。

---

## 1. 项目整体是什么

这是一个全栈项目，后端提供两类核心能力：

- **普通聊天（可选联网搜索）**：流式（SSE）输出回答，可做“对话记忆/联网增强/日志与落库”。
- **AI 智能客服（RAG）**：将 Markdown 知识库向量化存入 pgvector，通过向量检索拼接上下文，再让模型回答。

后端模块路径：`xiaoha-ai-robot-springboot/`

---

## 2. 技术栈与关键依赖（后端）

- **Spring Boot**：3.4.x（见 `pom.xml`）
- **JDK**：21（见 `pom.xml` 的 `<java.version>`）
- **Spring AI**：1.1.1（见 `pom.xml` 的 `<spring-ai.version>`）
- **OpenAI Compatible**：`spring-ai-starter-model-openai`
  - 本项目默认指向阿里云百炼 OpenAI 兼容接口（见 `application-dev.yml`）
- **数据库与向量库**：
  - PostgreSQL（业务数据 + 向量存储）
  - pgvector（向量检索）`spring-ai-starter-vector-store-pgvector`
  - MyBatis-Plus + p6spy（SQL/持久化）
- **联网搜索**：
  - SearXNG（搜索聚合服务）
  - OkHttp + Jsoup（抓取搜索结果页面内容的基础能力）

---

## 3. 你需要先建立的 3 个核心概念（Spring AI/RAG 入门）

### 3.1 ChatModel / ChatClient：模型调用抽象

本项目在 Controller 内构建 `ChatModel`（`OpenAiChatModel`），再用 `ChatClient` 发起对话调用：

- 非流式：`.call() ...`
- **流式**：`.stream()`（SSE 友好）

你可以把它理解为：**“一个可配置的 LLM Client + Prompt Builder + Stream 输出”**。

### 3.2 Advisor：对话链路中的“拦截器/中间件”

项目大量依赖 Advisor 机制来做：

- 调用前增强：改 Prompt、加上下文（memory、联网、RAG）
- 调用中处理：流式日志、边输出边落库

对应代码目录：`src/main/java/com/quanxiaoha/ai/robot/advisor/`

### 3.3 RAG：检索 + 拼上下文 + 让模型回答

RAG 的关键动作通常是：

1. `VectorStore.similaritySearch(...)` 拿到相关 `Document`
2. 把 `Document` 拼成 `{context}`，填入 `PromptTemplate`
3. 把“增强后的 Prompt”交回模型调用链

---

## 4. 后端“能力地图”：从入口开始看

> 建议你先从两个 Controller 入手，因为它们直接映射了产品功能。

### 4.1 普通聊天（流式 SSE + 记忆/联网 + 落库）

入口：`src/main/java/com/quanxiaoha/ai/robot/controller/ChatController.java`

重点接口：

- `POST /chat/new`：新建对话
- `POST /chat/completion`：**流式对话（SSE）**
- `POST /chat/list`、`POST /chat/message/list`：历史对话/消息查询

`/chat/completion` 的关键点（读代码时重点关注）：

- 动态模型与参数：请求中携带 `modelName/temperature/networkSearch`
- Advisor 选择逻辑：
  - **networkSearch=true**：`NetworkSearchAdvisor`（联网搜索增强）
  - **networkSearch=false**：`CustomChatMemoryAdvisor`（对话记忆增强）
- 通用后处理：
  - `CustomStreamLoggerAndMessage2DBAdvisor`：流式日志 + 消息落库（链路“侧写/审计/追踪”）

### 4.2 智能客服（RAG：向量检索增强）

入口：`src/main/java/com/quanxiaoha/ai/robot/controller/AiCustomerServiceController.java`

重点接口：

- `POST /customer-service/completion`：**智能客服流式对话（SSE）**
- 文件相关（知识库）：`/file/check`、`/file/upload-chunk`、`/file/merge-chunk`、`/file/list`、`/file/delete`、`/file/update`

`/customer-service/completion` 的关键点：

- 通过 `CustomerServiceAdvisor(vectorStore)` 做 RAG（向量检索增强）
- 最终 `.stream().content()` 流式输出

---

## 5. 四个最重要的 Advisor（按学习顺序）

### 5.1 对话记忆：`CustomChatMemoryAdvisor`

文件：`src/main/java/com/quanxiaoha/ai/robot/advisor/CustomChatMemoryAdvisor.java`

你要搞清楚：

- 记忆从哪里来：`ChatMessageMapper` 查询 DB
- 以什么格式注入 Prompt：是“拼历史消息”，还是“模板化注入”
- 记忆策略：默认“最新 N 条”（项目里常见是 50）

### 5.2 联网搜索增强：`NetworkSearchAdvisor`

文件：`src/main/java/com/quanxiaoha/ai/robot/advisor/NetworkSearchAdvisor.java`

链路理解（高频面试/实战点）：

1. 读取用户问题（`UserMessage`）
2. 调 `SearXNGService` 取搜索结果
3. 并发抓取网页正文（`SearchResultContentFetcherService.batchFetch`）
4. 构建 `context`，用 `PromptTemplate` 重建 Prompt
5. 把新 Prompt 交给 `streamAdvisorChain.nextStream(...)` 继续调用模型

### 5.3 客服 RAG：`CustomerServiceAdvisor`

文件：`src/main/java/com/quanxiaoha/ai/robot/advisor/CustomerServiceAdvisor.java`

链路理解：

1. `vectorStore.similaritySearch(query=用户问题, topK=3)`
2. 拼接 `Document` 文本成为 `{context}`
3. 使用模板生成增强 Prompt
4. 继续模型调用链

可改的业务点：

- `topK` 的值（3）与召回质量/上下文长度强相关
- 模板规则（回答格式、图片/链接输出规范等）

### 5.4 流式日志 + 落库：`CustomStreamLoggerAndMessage2DBAdvisor`

文件：`src/main/java/com/quanxiaoha/ai/robot/advisor/CustomStreamLoggerAndMessage2DBAdvisor.java`

你要搞清楚：

- 它如何接到流式 token
- 如何将用户/助手消息写入 `ChatMessage` 表（以及事务策略）

---

## 6. 知识库（Markdown → 向量化 → pgvector）链路

### 6.1 上传：分片上传与合并

实现：`src/main/java/com/quanxiaoha/ai/robot/service/impl/CustomerServiceImpl.java`

关键能力：

- 按 MD5 管理文件与分片，支持断点续传
- 分片元数据写入 `FileChunkInfo` 相关表（见 `domain/dos`、`domain/mapper`）
- 合并后触发向量化（通过 Spring 事件）

### 6.2 向量化：异步事件监听器

监听器：`src/main/java/com/quanxiaoha/ai/robot/event/listener/AiCustomerServiceMdUploadedListener.java`

核心步骤：

1. `MarkdownReader` 解析 Markdown → `List<Document>`
2. 去重：相似度检索命中且 score > 0.99 则跳过
3. `vectorStore.add(...)` 写入 pgvector
4. 更新文件处理状态（向量化中/完成/失败）

### 6.3 MarkdownReader：把 Markdown 变成可检索文档

文件：`src/main/java/com/quanxiaoha/ai/robot/reader/MarkdownReader.java`

学习要点：

- 分段粒度（chunk）如何决定召回质量与幻觉概率
- 元数据（metadatas）如何影响后续溯源/过滤

---

## 7. 配置与运行要点（开发环境）

配置文件：

- `src/main/resources/application.yml`（激活 `dev` profile）
- `src/main/resources/application-dev.yml`

你需要重点关注：

- **LLM 配置**：
  - `spring.ai.openai.base-url`
  - `spring.ai.openai.api-key`（默认从环境变量 `BAILIAN_API_KEY` 读取）
  - embedding 模型与维度（默认 1536）
- **数据库**：
  - `spring.datasource.url/username/password`
- **pgvector**：
  - `spring.ai.vectorstore.pgvector.table-name`（默认 `t_vector_store`）
  - 维度、索引类型（HNSW）、距离类型（COSINE）
- **SearXNG**（可选）：
  - `searxng.url`

---

## 8. 1 天掌握项目的建议节奏（强实践）

### 8.1 第 1 小时：跑通一次“普通聊天”链路

- 调用 `/chat/completion`（开启/关闭联网各一次）
- 观察：
  - SSE 是否持续返回
  - DB 里是否写入 `ChatMessage`
  - 日志是否输出增强后的 Prompt（便于理解 Advisor 效果）

### 8.2 第 2~3 小时：精读 2 个 Controller + 4 个 Advisor

只要能复述清楚：

- “不开联网 → 记忆增强”
- “开联网 → 搜索增强”
- “客服 → 向量检索增强”
- “流式输出 → 日志/落库”

你基本就能接手改需求。

### 8.3 第 4~6 小时：走通“知识库上传 → 向量化 → 检索回答”

- 上传 Markdown（分片/合并）
- 等待异步向量化完成
- 提问，确认 RAG 生效（回答明显引用了知识库内容）

---

## 9. 三个最推荐的上手改动（练习）

- **练习 A（Prompt 工程）**：修改 `NetworkSearchAdvisor` 或 `CustomerServiceAdvisor` 的模板，让回答固定成“要点 + 证据来源 + 总结”。
- **练习 B（检索质量）**：将 `topK(3)` 改成配置项（例如 `customer-service.topK`），对比回答变化。
- **练习 C（向量化策略）**：调整向量化去重阈值（0.99）或 Markdown 分段策略，观察召回与重复率。

---

## 10. 你接下来想怎么深入？

你可以直接告诉我你想实现的目标，我可以给你一份“改动点清单”（改哪些类/方法、如何验证）：

- 普通聊天也接入 RAG（统一知识库）
- 联网回答强制必须带来源链接
- 记忆从“最近 N 条”改成“按 token 长度裁剪”
- 向量检索支持按文档/标签过滤（metadata filter）

