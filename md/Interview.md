# 小哈 AI 机器人 - 面试项目说明（基于真实代码）

> 本文档根据仓库实际代码整理，方便在面试中「按代码讲清楚」项目，即使项目不是自己写的也能说得有据可查。

---

## 一、项目一句话介绍

**小哈 AI 机器人**：基于 Spring Boot 3.x + Spring AI 的智能对话系统，支持**普通对话（带记忆）**、**联网搜索增强**、**RAG 智能客服**三种模式；后端用 PostgreSQL（业务 + pgvector 向量库），搜索用自建 SearXNG，通过 Advisor 链式增强 prompt，SSE 流式输出。

---

## 二、技术栈（与简历对应）

| 简历表述 | 代码/配置对应 |
|---------|----------------|
| Spring Boot 3.x | `pom.xml` + `XiaohaAiRobotSpringbootApplication.java` |
| PostgreSQL + pgvector | `application-dev.yml`：`spring.datasource.url`、`spring.ai.vectorstore.pgvector` |
| Spring AI | `ChatController`、各 Advisor、`VectorStore` |
| 阿里云百炼 | `application-dev.yml`：`spring.ai.openai.base-url`（dashscope 兼容） |
| SearXNG | `application-dev.yml`：`searxng.url`；`SearXNGServiceImpl` |
| OkHttp3 | `OkHttpConfig`、`SearXNGServiceImpl`、`SearchResultContentFetcherServiceImpl` |
| Jsoup | `SearchResultContentFetcherServiceImpl`：`Jsoup.parse(html).text()` |
| CompletableFuture + 自定义线程池 | `SearchResultContentFetcherServiceImpl.batchFetch`、`ThreadPoolConfig`（httpRequestExecutor、resultProcessingExecutor） |
| Spring Event | `AiCustomerServiceMdUploadedEvent`、`AiCustomerServiceMdUploadedListener`、`AsyncEventConfig`（eventTaskExecutor） |
| MyBatis-Plus | `domain/dos/`、`domain/mapper/`、`MybatisPlusConfig` |

---

## 三、简历每条对应的真实实现（面试怎么讲）

### 1. 集成 PostgreSQL 作为会话与向量存储数据库，实现聊天记忆与长上下文管理

**代码位置：**

- 配置：`application-dev.yml`  
  - 数据源：`spring.datasource.url` → `jdbc:postgresql://localhost:5432/robot`  
  - 向量：`spring.ai.vectorstore.pgvector`（table-name: t_vector_store，index-type: HNSW，distance-type: COSINE_DISTANCE）
- 对话/消息表：`ChatDO`（t_chat）、`ChatMessageDO`（t_chat_message），Mapper：`ChatMapper`、`ChatMessageMapper`
- 记忆逻辑：`CustomChatMemoryAdvisor`

**面试话术：**

- 「业务库和向量库都用 PostgreSQL：业务表是 `t_chat`、`t_chat_message`，向量表是 `t_vector_store`，用 pgvector 的 HNSW + 余弦距离。」
- 「长上下文是**对话记忆 Advisor**做的：每次请求前根据 `chatId` 从 `ChatMessageMapper` 查该会话**最近 50 条**消息（`orderByDesc createTime` + `LIMIT 50`），按时间升序转成 Spring AI 的 `UserMessage`/`AssistantMessage`，和当前用户消息一起塞进 `ChatClientRequest.prompt().messages()`，再调模型。所以模型能看到最近 50 轮对话。」
- 追问「为什么 50 条」：可说「避免单次 prompt 过长，50 条是项目里配置的，可以按 token 或条数做成可配置。」

---

### 2. 自定义 Advisor：历史注入、消息持久化、流式/非流式收集

**代码位置：**

- 历史注入：`CustomChatMemoryAdvisor`（order=2，先于落库执行）
- 流式日志 + 落库：`CustomStreamLoggerAndMessage2DBAdvisor`（order=99，最后执行）
- 落库：`ChatMessageMapper.insert`，用户消息一条、AI 回复一条；流式用 `AtomicReference<StringBuilder>` 拼完整内容，在 `doOnComplete` 里一次性写入；事务：`TransactionTemplate`

**面试话术：**

- 「我们用了两个自定义 **StreamAdvisor**：一个是**对话记忆**，从 DB 拉历史消息注入 prompt；一个是**流式日志+落库**，在流式返回时用 `StringBuilder` 把每个 chunk 拼成完整内容，在 `doOnComplete` 里用 `TransactionTemplate` 开事务，先 insert 用户消息，再 insert AI 消息（含 content 和 reasoningContent），这样流式、非流式都能完整落库。」
- 「Advisor 的执行顺序是靠 `getOrder()`：记忆是 2，落库是 99，所以先增强 prompt 再调模型，最后在流结束时写库。」

---

### 3. 自部署 SearXNG + OkHttp3 + CompletableFuture + Jsoup，联网搜索与降 Token

**代码位置：**

- 搜索：`SearXNGServiceImpl` — 用 `OkHttpClient` 调 `searxng.url`（GET，参数 q、format=json、engines=...），解析 JSON 取 results，按 score 降序、limit count（配置里 10 条），封装成 `SearchResultDTO`（url、score）。
- 抓取正文：`SearchResultContentFetcherServiceImpl`  
  - `batchFetch(searchResults, 7, TimeUnit.SECONDS)`：每个 result 一个 `CompletableFuture.supplyAsync(()-> syncFetchHtmlContent(url), httpExecutor)`，再 `completeOnTimeout`、`exceptionally` 兜底，最后 `CompletableFuture.allOf` + `thenApplyAsync` 用 Jsoup 统一做 `Jsoup.parse(html).text()` 填回 `SearchResultDTO.content`，用 `processingExecutor` 线程池。
- 使用处：`NetworkSearchAdvisor` 先 `searXNGService.search`，再 `searchResultContentFetcherService.batchFetch(..., 7, SECONDS)`，用拼好的 context 填 `PromptTemplate`，再 `streamAdvisorChain.nextStream`。

**面试话术：**

- 「联网搜索是 **NetworkSearchAdvisor**：先调 **SearXNG** 拿到一批 URL 和评分，再用 **CompletableFuture** 并发请求每个 URL 的 HTML，用**自定义线程池** httpExecutor 做 IO，7 秒超时；拿到 HTML 后用 **Jsoup** 做 `parse(html).text()` 只留正文，去掉脚本和样式，明显减少送给模型的 token，我们按字符/预估 token 对比过，能降一大半。」
- 「线程池有两类：http 请求用 IO 密集型池（核心 50、最大 200），结果处理用 CPU 池（核心=CPU 数），在 `ThreadPoolConfig` 里配置的。」

---

### 4. 自定义 RAG Advisor：私有知识库、增强提示词、智能客服

**代码位置：**

- RAG 逻辑：`CustomerServiceAdvisor`  
  - 用注入的 `VectorStore` 做 `vectorStore.similaritySearch(SearchRequest.builder().query(用户问题).topK(3).build())`，拿到 `List<Document>`，拼成 context 字符串。
  - 用类里静态 `PromptTemplate` 填 `context`、`question`，规则是「只根据上下文回答、无法回答时统一回复加微信」等，再 `streamAdvisorChain.nextStream(newChatClientRequest)`。
- 向量库：同上，pgvector，表 `t_vector_store`；数据来源是客服知识库 Markdown 上传后解析、向量化写入。

**面试话术：**

- 「智能客服是 **CustomerServiceAdvisor**：用户问题进来先走 **VectorStore.similaritySearch**，topK=3，从 pgvector 拉相似文档，把文档内容拼成 context，再套进**固定提示词模板**（角色是小哈客服、只许根据上下文回答、不能回答就说加微信），然后调模型。这样就是按指定语料的问答，不会乱编。」
- 「知识库数据是 Markdown 上传后，由事件监听器解析、切片、向量化写入 pgvector，和这个 Advisor 是分开的两块。」

---

### 5. 提示词工程：多场景、角色/规则/上下文

**代码位置：**

- 联网搜索：`NetworkSearchAdvisor` 里的 `DEFAULT_PROMPT_TEMPLATE`（综合分析上下文、标注来源链接等）。
- 客服：`CustomerServiceAdvisor` 里的 `DEFAULT_PROMPT_TEMPLATE`（角色、核心规则、回答范围、无法回答时的统一话术、图片 Markdown 等）。

**面试话术：**

- 「提示词是写在各自 Advisor 里的 **PromptTemplate**：联网搜索强调『根据上下文、标注来源、避免说根据上下文』；客服强调『严格基于上下文、无法回答时统一回复』。通过角色设定和规则约束，模型在各自场景里更稳定、少幻觉。」

---

### 6. 基于 Spring Boot 3.x 搭建后端服务：对话管理、消息分页、知识库 Markdown 管理

**代码位置：**

- 对话/消息：`ChatController`（`/chat/new`、`/chat/completion`、`/chat/list`、`/chat/message/list`、`/chat/summary/rename`、`/chat/delete`），`ChatService`/`ChatServiceImpl`，分页用 `FindChatHistoryMessagePageListReqVO` 等。
- 知识库/客服：`AiCustomerServiceController`（文件上传、分片、合并、列表、删除等），`CustomerServiceImpl`，表 `AiCustomerServiceFileStorageDO`、`FileChunkInfoDO`，配置里 `customer-service.file-storage-path`、`chunk-path`。

**面试话术：**

- 「后端是 Spring Boot 3.x，两大块接口：**ChatController** 管对话的创建、流式 completion、历史列表、消息分页、重命名、删除；**AiCustomerServiceController** 管客服知识库的 Markdown 分片上传、合并、文件列表、删除。分页都是查 DB 用 MyBatis-Plus 的 Page 或自定义分页参数。」

---

### 7. Spring Event 发布/订阅：文件上传后解析、向量化异步解耦

**代码位置：**

- 事件：`AiCustomerServiceMdUploadedEvent`（id、filePath、metadatas）。
- 发布：`CustomerServiceImpl` 在**分片合并完成后**（约 473–487 行）`eventPublisher.publishEvent(AiCustomerServiceMdUploadedEvent.builder().id(id).filePath(...).metadatas(...).build())`。
- 监听：`AiCustomerServiceMdUploadedListener.vectorizing` 方法上 `@EventListener` + `@Async("eventTaskExecutor")`，里面对该文件：先更新状态为「向量化中」，再用 `TransactionTemplate` 里 `MarkdownReader.loadMarkdown` 解析为 `List<Document>`，对每个 document 先 similaritySearch topK=1 做**去重**（score>0.99 跳过），再 `vectorStore.add`，最后更新状态为已完成或失败。线程池：`AsyncEventConfig.eventTaskExecutor`（核心 5，最大 10）。

**面试话术：**

- 「文件上传是分片上传、合并落盘后，只做**存库 + 发事件**，不在这里做解析和向量化。我们发的是 **AiCustomerServiceMdUploadedEvent**，带文件 ID、路径和元数据。监听器用 **@EventListener + @Async("eventTaskExecutor")**，在单独线程池里做：读 Markdown、用 **MarkdownReader**（按水平线切段、可排除代码块等）转成 Document 列表，再逐条做相似度去重后 **vectorStore.add**，并更新文件状态。这样上传接口很快返回，解析和向量化异步执行，用 **TransactionTemplate** 保证写入和状态更新的一致性。」

---

## 四、Advisor 链与接口对应关系（必记）

- **普通对话（不联网）**：`CustomChatMemoryAdvisor`(order=2) → `CustomStreamLoggerAndMessage2DBAdvisor`(order=99)。  
  入口：`POST /chat/completion`，`networkSearch=false`。
- **联网对话**：`NetworkSearchAdvisor`(order=1) → `CustomStreamLoggerAndMessage2DBAdvisor`(order=99)。  
  入口：`POST /chat/completion`，`networkSearch=true`。（注意：联网时没有记忆 Advisor，代码里二选一。）
- **智能客服**：`CustomerServiceAdvisor`(order=1) + 流式落库（若有）。  
  入口：`POST /customer-service/completion`（需看 `AiCustomerServiceController` 是否也挂了类似落库 Advisor）。

建议面试前打开 `ChatController.chat` 方法看一遍 `advisors` 的组装逻辑，能说清「什么时候用记忆、什么时候用搜索、什么时候用 RAG」。

---

## 五、面试前自检清单

1. 能说出**两个表**：`t_chat`、`t_chat_message`，以及向量表 `t_vector_store`。
2. 能说出**四个 Advisor** 的类名与作用：`CustomChatMemoryAdvisor`、`NetworkSearchAdvisor`、`CustomerServiceAdvisor`、`CustomStreamLoggerAndMessage2DBAdvisor`。
3. 能说出**事件名**与**触发时机**：`AiCustomerServiceMdUploadedEvent`，在客服文件**合并完成后**发布；谁监听：`AiCustomerServiceMdUploadedListener`，做什么：解析 Markdown、去重、向量化、更新状态。
4. 能说出**联网搜索链路**：SearXNG 拿 URL → OkHttp + CompletableFuture 并发抓 HTML → Jsoup 取正文 → 拼进 PromptTemplate → 调模型。
5. 能说出**线程池**：http 抓取用 `httpRequestExecutor`，事件处理用 `eventTaskExecutor`，结果处理用 `resultProcessingExecutor`（在 `ThreadPoolConfig`/`AsyncEventConfig`）。

---

## 六、若被问「项目不是你自己写的怎么办」

可以这样表述（按真实情况选）：

- 「项目是团队/开源项目，我**通读并调试过**后端核心模块，包括 Advisor 链、事件监听和搜索链路，能讲清楚数据流和设计取舍。」
- 「我**负责/参与**了其中 [记忆/联网/RAG/事件 等] 部分的改造/配置/排查，所以对这块实现比较熟。」

把本文档里的「代码位置」和「面试话术」和仓库里的类名、方法名对一下，面试时按「类名 + 做了什么」来讲，会很有说服力。
