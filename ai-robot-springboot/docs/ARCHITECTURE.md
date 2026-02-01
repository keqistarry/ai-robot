# 小哈 AI 机器人 - 后端整体框架架构设计

## 1. 项目概述

小哈 AI 机器人后端是基于 **Spring Boot 3.x + Spring AI** 的智能对话服务，提供三类核心能力：

| 能力 | 说明 |
|------|------|
| **普通聊天（带记忆）** | 流式 SSE 对话，支持历史消息注入与实时落库 |
| **联网搜索增强** | 接入 SearXNG 聚合搜索，并发抓取网页正文后拼上下文再调用模型 |
| **RAG 智能客服** | 基于 pgvector 向量检索私有知识库，按指定语料回答 |

通过 **Spring AI Advisor** 机制将「记忆 / 联网 / RAG / 流式落库」模块化组合，实现可扩展的对话增强链路。

---

## 2. 技术栈

| 类别 | 技术 | 版本/说明 |
|------|------|-----------|
| 基础框架 | Spring Boot | 3.4.5 |
| JDK | Java | 21 |
| AI 框架 | Spring AI | 1.1.1 |
| 模型接入 | spring-ai-starter-model-openai | OpenAI 兼容（默认阿里云百炼） |
| 业务库 | PostgreSQL | 业务表 + 向量存储 |
| 向量存储 | pgvector | spring-ai-starter-vector-store-pgvector |
| ORM | MyBatis-Plus | 3.5.12 |
| SQL 监控 | p6spy | 3.9.1 |
| 联网搜索 | SearXNG | 自部署，OkHttp 调用 |
| 网页抓取/清洗 | OkHttp + Jsoup | 4.12.0 / 1.17.2 |
| 日志 | Log4j2 | 替代默认 Logback |
| 其他 | Hutool、Guava、Lombok、Validation、AOP | 工具与校验 |

---

## 3. 整体架构图

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    前端 / 客户端                           │
                    └───────────────────────────┬─────────────────────────────┘
                                                │ HTTP / SSE
                    ┌───────────────────────────▼─────────────────────────────┐
                    │              Controller 层（接口入口）                     │
                    │  ChatController  │  AiCustomerServiceController          │
                    │  /chat/*         │  /customer-service/*                  │
                    └───────────────────────────┬─────────────────────────────┘
                                                │
        ┌───────────────────────────────────────┼───────────────────────────────────────┐
        │                                       │                                       │
        ▼                                       ▼                                       ▼
┌───────────────┐                    ┌─────────────────────┐                ┌─────────────────────┐
│ ChatService   │                    │ Spring AI ChatClient │                │ CustomerService      │
│ 对话 CRUD     │                    │ + Advisor 链         │                │ 知识库文件/分片/合并 │
└───────┬───────┘                    └──────────┬───────────┘                └──────────┬──────────┘
        │                                        │                                        │
        │              ┌─────────────────────────┼─────────────────────────┐              │
        │              │                         │                         │              │
        ▼              ▼                         ▼                         ▼              ▼
┌───────────────┐  ┌───────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ MyBatis-Plus  │  │ MemoryAdvisor │  │ NetworkSearch   │  │ CustomerService │  │ EventPublisher  │
│ t_chat        │  │ 历史消息注入   │  │ Advisor         │  │ Advisor         │  │ 上传完成 → 向量化 │
│ t_chat_message│  │               │  │ SearXNG+抓取    │  │ pgvector 检索   │  │ @Async 线程池     │
└───────────────┘  └───────────────┘  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘
        │              │                         │                         │              │
        │              │                         ▼                         │              ▼
        │              │              ┌─────────────────┐                   │     ┌─────────────────┐
        │              │              │ SearXNGService  │                   │     │ MarkdownReader   │
        │              │              │ ContentFetcher  │                   │     │ VectorStore.add  │
        │              │              │ OkHttp+Jsoup    │                   │     │ 去重/状态机      │
        │              │              └─────────────────┘                   │     └─────────────────┘
        │              │                         │                         │              │
        ▼              ▼                         ▼                         ▼              ▼
┌───────────────────────────────────────────────────────────────────────────────────────────────────┐
│                          PostgreSQL（业务表）  +  pgvector（t_vector_store）                         │
└───────────────────────────────────────────────────────────────────────────────────────────────────┘
        │                                                                              │
        ▼                                                                              ▼
  外部依赖（可选）:  OpenAI 兼容 API（百炼）、SearXNG 服务、目标网页
```

---

## 4. 模块划分与职责

### 4.1 包结构一览

```
com.quanxiaoha.ai.robot
├── XiaohaAiRobotSpringbootApplication.java   # 启动类
├── advisor/                                  # Spring AI 对话增强链
│   ├── CustomChatMemoryAdvisor               # 对话记忆（DB 拉历史注入 Prompt）
│   ├── NetworkSearchAdvisor                  # 联网搜索（SearXNG + 抓取 + 模板）
│   ├── CustomerServiceAdvisor                # RAG 客服（pgvector 检索 + 模板）
│   └── CustomStreamLoggerAndMessage2DBAdvisor # 流式日志 + 用户/助手消息落库
├── aspect/                                   # AOP
│   ├── ApiOperationLog                       # 接口日志注解
│   └── ApiOperationLogAspect                 # 入参/出参/耗时环绕
├── config/                                   # 配置类
│   ├── AsyncEventConfig                      # @Async 事件线程池 eventTaskExecutor
│   ├── ChatClientConfig                      # （若有）ChatClient 相关 Bean
│   ├── CorsConfig                            # 跨域
│   ├── JacksonConfig                         # JSON 序列化
│   ├── MybatisPlusConfig                     # 分页等
│   ├── OkHttpConfig                          # OkHttp 客户端
│   └── ThreadPoolConfig                      # httpRequestExecutor / resultProcessingExecutor
├── constant/                                 # 常量
├── controller/                               # 接口层
│   ├── ChatController                        # /chat/* 普通对话
│   └── AiCustomerServiceController           # /customer-service/* 客服与知识库
├── domain/                                   # 数据层
│   ├── dos/                                  # 表实体
│   │   ├── ChatDO, ChatMessageDO
│   │   ├── AiCustomerServiceFileStorageDO, FileChunkInfoDO
│   └── mapper/                               # MyBatis-Plus Mapper
├── enums/                                    # 枚举（状态码、文件状态等）
├── event/                                    # 领域事件
│   ├── AiCustomerServiceMdUploadedEvent      # 文件合并完成事件
│   └── listener/
│       └── AiCustomerServiceMdUploadedListener # 异步向量化
├── exception/                                # 异常与全局处理
│   ├── BaseExceptionInterface, BizException
│   └── GlobalExceptionHandler
├── model/                                    # DTO/VO/公共查询
│   ├── common/, dto/, vo/chat/, vo/customerService/
├── reader/                                   # 文档解析
│   └── MarkdownReader                        # Markdown → Document 列表
├── service/                                  # 业务接口与实现
│   ├── ChatService, CustomerService
│   ├── SearXNGService, SearchResultContentFetcherService
│   └── impl/
└── utils/                                    # 工具类（Response、PageResponse、JsonUtil 等）
```

### 4.2 核心模块职责

| 模块 | 职责 |
|------|------|
| **Controller** | 接收 HTTP/SSE 请求，组装 ChatModel/ChatClient 与 Advisor 列表，返回统一 Response/Flux |
| **Advisor** | 在模型调用链中插入逻辑：改 Prompt（记忆/联网/RAG）、流式侧写（日志+落库），order 控制执行顺序 |
| **Service** | 对话 CRUD、知识库分片上传/合并/列表/删除、SearXNG 搜索、网页内容批量抓取与清洗 |
| **Event + Listener** | 文件合并完成后发布事件，异步线程池执行 Markdown 解析、向量化、状态更新 |
| **Config** | 线程池、OkHttp、CORS、异步、MyBatis-Plus、pgvector 等 |
| **Exception** | BizException + 全局 Handler，统一返回码与错误信息 |

---

## 5. 核心业务流程

### 5.1 普通聊天（带记忆）— `/chat/completion`

1. 前端 POST 请求体：`message`、`chatId`、`modelName`、`temperature`、`networkSearch=false`。
2. Controller 构建 `OpenAiChatModel`，`ChatClient.prompt().user(message).options(...)`。
3. 若 **未开启联网**：加入 `CustomChatMemoryAdvisor`（从 DB 按 `chatId` 拉最近 N 条消息，拼入 Prompt）。
4. 加入 `CustomStreamLoggerAndMessage2DBAdvisor`（流式消费 token，聚合完整回答与推理内容，流结束后事务写入 `t_chat_message` 两条：user + assistant）。
5. 返回 `Flux<AIResponse>`，SSE 推送到前端。

**Advisor 顺序**：Memory(order=2) → StreamLoggerAndMessage2DB(order=99)。

### 5.2 联网搜索增强 — `/chat/completion`（networkSearch=true）

1. 不挂 Memory Advisor，改为挂 `NetworkSearchAdvisor`（order=1）。
2. 取用户问题 → 调 `SearXNGService.search(query)` 拿 URL 列表。
3. `SearchResultContentFetcherService.batchFetch()`：CompletableFuture + 双线程池（httpRequestExecutor / resultProcessingExecutor）并发抓取 HTML，Jsoup 提正文，超时/异常降级为空。
4. 用 `PromptTemplate` 把「问题 + 上下文」拼成新 Prompt，交给链继续流式调用模型。
5. 仍由 `CustomStreamLoggerAndMessage2DBAdvisor` 做流式日志与落库。

### 5.3 RAG 智能客服 — `/customer-service/completion`

1. 仅挂 `CustomerServiceAdvisor`（order=1）。
2. 用户问题 → `VectorStore.similaritySearch(query, topK=3)` 得到 Document 列表。
3. 将 Document 文本拼成 `context`，通过 `PromptTemplate` 生成「角色 + 上下文 + 问题 + 回答要求」的增强 Prompt。
4. 流式返回 `Flux<AIResponse>`，无单独落库（可按需扩展）。

### 5.4 知识库：分片上传 → 合并 → 向量化

1. **检查** `POST /customer-service/file/check`：按 `fileMd5` 查是否已存在，存在则返回已上传分片列表（断点续传）或秒传。
2. **分片上传** `POST /customer-service/file/upload-chunk`：按 `fileMd5 + chunkNumber` 落盘并写 `t_file_chunk_info`，更新 `t_ai_customer_service_file_storage.uploadedChunks`。
3. **合并** `POST /customer-service/file/merge-chunk`：按序号读分片，写入最终文件，更新状态为 PENDING，删除分片记录与临时目录，发布 `AiCustomerServiceMdUploadedEvent`。
4. **异步向量化**：`AiCustomerServiceMdUploadedListener`（@Async("eventTaskExecutor")）消费事件，将状态置为 VECTORIZING，用 `MarkdownReader` 解析为 `List<Document>`，对每条做相似度去重（score>0.99 跳过）后 `vectorStore.add()`，最后状态置为 COMPLETED/FAILED。

---

## 6. 数据模型与存储

### 6.1 业务表（PostgreSQL）

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| t_chat | 对话 | id, uuid, summary, create_time, update_time |
| t_chat_message | 聊天消息 | id, chat_uuid, content, reasoning_content, role, create_time |
| t_ai_customer_service_file_storage | 知识库文件 | id, file_md5, file_name, file_path, file_size, total_chunks, uploaded_chunks, status, ... |
| t_file_chunk_info | 分片信息 | id, file_md5, chunk_number, chunk_path, chunk_size |

### 6.2 文件状态机（status）

- **UPLOADING(0)**：分片上传中  
- **PENDING(1)**：合并完成，待向量化  
- **VECTORIZING(2)**：向量化中  
- **COMPLETED(3)**：完成  
- **FAILED(4)**：失败  

### 6.3 向量存储（pgvector）

- 表名：`t_vector_store`（由 Spring AI pgvector 自动管理）。  
- 用途：存储 Markdown 解析后的 Document 向量，供 RAG 相似度检索。  
- 元数据示例：`mdStorageId`、`originalFileName` 等，用于溯源与删除。

---

## 7. 配置与部署要点

- **环境**：`application.yml` 激活 `dev`，实际 LLM/DB/SearXNG 等在 `application-dev.yml` 中配置。  
- **LLM**：`spring.ai.openai.base-url`、`spring.ai.openai.api-key`（如 `${BAILIAN_API_KEY}`）、embedding 模型与维度。  
- **数据库**：`spring.datasource.*`，可选 p6spy 代理 URL。  
- **pgvector**：`spring.ai.vectorstore.pgvector`（table-name、dimensions、index-type、distance-type）。  
- **SearXNG**：`searxng.url`、`searxng.count`。  
- **customer-service**：`file-storage-path`、`chunk-path`、`model`、`temperature`。  
- **线程池**：HTTP 抓取与结果处理分离（ThreadPoolConfig），事件异步用 `eventTaskExecutor`（AsyncEventConfig）。

---

## 8. 小结

- **入口**：两个 Controller（Chat / AiCustomerService），分别对应「普通聊天+联网」与「RAG 客服+知识库管理」。  
- **增强链**：通过 Advisor 组合「记忆 / 联网 / RAG / 流式落库」，顺序由 order 控制。  
- **数据**：业务表用 MyBatis-Plus，向量用 pgvector；知识库采用分片上传 + 事件驱动异步向量化，状态机清晰。  
- **可观测**：AOP 接口日志、p6spy SQL、流式完整内容落库，便于排查与审计。

本文档与当前代码结构一致，可作为新人上手与架构评审的参考。
