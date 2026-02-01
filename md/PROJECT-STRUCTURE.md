# 项目结构（xiaoha-ai-robot）

本仓库是一个**前后端分离**项目：

- `xiaoha-ai-robot-springboot/`：后端（Spring Boot + Spring AI + pgvector）
- `xiaoha-ai-robot-vue3/`：前端（Vue3 + Vite，SSE 流式渲染）

---

## 目录树（概览）

```text
xiaoha-ai-robot/
├── PROJECT-STRUCTURE.md
├── xiaoha-ai-robot-springboot/
│   ├── pom.xml
│   ├── README-backend.md
│   └── src/
│       ├── main/
│       │   ├── java/com/quanxiaoha/ai/robot/
│       │   │   ├── XiaohaAiRobotSpringbootApplication.java
│       │   │   ├── advisor/
│       │   │   ├── aspect/
│       │   │   ├── config/
│       │   │   ├── constant/
│       │   │   ├── controller/
│       │   │   ├── domain/
│       │   │   ├── enums/
│       │   │   ├── event/
│       │   │   ├── exception/
│       │   │   ├── model/
│       │   │   ├── reader/
│       │   │   ├── service/
│       │   │   └── utils/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       ├── log4j2.xml
│       │       └── spy.properties
│       └── test/java/com/quanxiaoha/ai/robot/
│           ├── MybatisPlusTests.java
│           └── XiaohaAiRobotSpringbootApplicationTests.java
└── xiaoha-ai-robot-vue3/
    ├── package.json
    ├── vite.config.js
    ├── index.html
    ├── README.md
    └── src/
        ├── main.js
        ├── App.vue
        ├── router/
        ├── stores/
        ├── api/
        ├── views/
        ├── components/
        ├── layouts/
        ├── assets/
        └── axios.js
```

---

## 后端（`xiaoha-ai-robot-springboot/`）

### 入口与配置

- **启动类**：`src/main/java/com/quanxiaoha/ai/robot/XiaohaAiRobotSpringbootApplication.java`
- **配置**：
  - `src/main/resources/application.yml`：激活 profile（默认 `dev`）
  - `src/main/resources/application-dev.yml`：开发环境（DB、LLM、pgvector、SearXNG 等）
  - `src/main/resources/application-prod.yml`：生产环境（当前为空/待补）
  - `src/main/resources/log4j2.xml`：日志配置
  - `src/main/resources/spy.properties`：p6spy 配置（SQL 打印/审计）

### 核心分层（你应该从哪看起）

- **`controller/`**：HTTP 入口（REST + SSE）
  - `ChatController.java`：普通聊天（`/chat/*`）
  - `AiCustomerServiceController.java`：智能客服（`/customer-service/*`，含文件上传/合并/管理 + SSE 对话）
- **`advisor/`**：Spring AI Advisor（Prompt 增强/记忆/联网/RAG/落库）
  - `CustomChatMemoryAdvisor.java`：对话记忆（从 DB 拉历史消息注入 prompt）
  - `NetworkSearchAdvisor.java`：联网搜索增强（SearXNG + 页面内容抓取 + prompt 重建）
  - `CustomerServiceAdvisor.java`：RAG（向量检索增强，`VectorStore.similaritySearch`）
  - `CustomStreamLoggerAndMessage2DBAdvisor.java`：流式日志 + 消息落库
- **`service/`**：业务服务接口与实现（`impl/`）
  - `ChatService*`：对话管理、历史记录等
  - `CustomerService*`：客服文件上传（分片）、合并、状态管理等
  - `SearXNGService*`：调用 SearXNG 搜索
  - `SearchResultContentFetcherService*`：并发抓取搜索结果网页正文
- **`domain/`**：持久化对象与 Mapper（MyBatis-Plus）
  - `dos/`：数据库 DO（如 `ChatDO`、`ChatMessageDO`、`FileChunkInfoDO` 等）
  - `mapper/`：Mapper 接口
- **`event/`**：事件驱动链路
  - `AiCustomerServiceMdUploadedEvent.java`：上传完成事件
  - `listener/AiCustomerServiceMdUploadedListener.java`：异步向量化入库（Markdown → Document → pgvector）
- **`reader/`**：内容解析
  - `MarkdownReader.java`：将 Markdown 解析/切分为 Spring AI `Document`
- **`model/`**：入参/出参模型（VO/DTO）
  - `vo/`：Controller 请求/响应 VO（聊天、客服、文件上传等）
  - `dto/`：内部数据传输（如搜索结果）
- **`exception/`**：统一异常与业务异常
- **`config/`**：线程池、跨域、MyBatis、OkHttp、Jackson 等配置
- **`aspect/`**：接口日志切面（`@ApiOperationLog`）
- **`utils/`**：统一返回体、分页、JSON 工具等

---

## 前端（`xiaoha-ai-robot-vue3/`）

### 入口与工程配置

- **入口**：`src/main.js`、`src/App.vue`
- **构建/开发**：`vite.config.js`
- **依赖**：`package.json`（含 `@microsoft/fetch-event-source`、`ant-design-vue`、`pinia`、`tailwindcss` 等）

### 关键目录

- **`views/`**：页面
  - `ChatPage.vue`：普通聊天（SSE 调 `POST /chat/completion`）
  - `CustomerServiceChatPage.vue`：智能客服（SSE 调 `POST /customer-service/completion` + 文件分片上传/合并）
  - `Index.vue`：入口页
- **`api/`**：后端接口封装
  - `chat.js`、`customerService.js`
- **`components/`**：通用组件（输入框、侧边栏、流式 Markdown 渲染等）
- **`stores/`**：Pinia 状态管理（如 `chatStore.js`）
- **`router/`**：路由（`router/index.js`）
- **`axios.js`**：Axios 全局实例/拦截器（如果有）
- **`assets/`**：样式与图标资源

---

## 推荐阅读顺序（新人最省时间）

- 先看后端：`controller/` → `advisor/` → `service/` → `event/`（向量化）→ `domain/`
- 再看前端：`views/ChatPage.vue` / `views/CustomerServiceChatPage.vue` → `api/` → `components/StreamMarkdownRender.vue`

