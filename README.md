## Technology Stack
Spring Boot 3.x, JDK 21, Spring AI, DeepSeek, MyBatis Plus, PostgreSQL, RAG, SSE, SearXNG, OkHttp3, Jsoup, Docker

## Project Description
An intelligent dialogue platform built on the **Spring AI** framework, featuring two AI-powered applications:  
- A **general-purpose chatbot**  
- An **intelligent customer service system** based on a private knowledge base (RAG-enabled)

## Job Responsibilities
- Integrated **PostgreSQL** as both the session store and vector database (via **pgvector**) to support chat memory, long-context management, conversation history pagination, and **RAG-based vector retrieval**.
- Customized the **Advisor component** in the Spring AI invocation chain to automatically inject historical dialogues, persist all messages, fully capture **SSE streaming responses**, and store them in the database for auditing and analytics.
- Deployed the **SearXNG** meta-search engine and implemented concurrent backend requests using **OkHttp3 + CompletableFuture**; utilized **Jsoup** to extract and clean main article content, effectively controlling context length and LLM token consumption while enabling real-time internet search augmentation.
- Developed a **custom RAG Advisor** that leverages **pgvector** to retrieve relevant chunks from private knowledge bases and dynamically construct enriched prompts, delivering accurate “Q&A based on specified corpora” capabilities for intelligent customer service.
- Designed and optimized **prompts** across multiple scenarios (e.g., web search augmentation, customer support), improving large model **accuracy, reliability, and output consistency** through role definition, rule constraints, and contextual guidance.
- Built the core backend service using **Spring Boot 3.x**, implementing features such as conversation management, message pagination, and Markdown-based knowledge base upload & chunk merging. Decoupled post-upload file parsing and vectorization using **Spring Events**, significantly enhancing modularity, maintainability, and testability.
