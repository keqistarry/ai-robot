package com.quanxiaoha.ai.robot.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * @Description: 智能客服 Advisor
 */
@Slf4j
public class CustomerServiceAdvisor implements StreamAdvisor {

    private final VectorStore vectorStore;

    /**
     * 联网搜索提示词模板
     */
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
            你是一个专业的客服，名为 “小哈 AI 助手”。请根据以下上下文信息回答用户问题。
            
            ## 上下文信息
            {context}

            请根据上下文内容来回复用户：
            
            ## 用户问题
            {question}
            
            ## 回答要求
            
            **核心规则**：
            1. **严格基于上下文**：只能使用提供的上下文信息回答问题
            2. **服务风格**：热情、耐心、专业，可以使用适当的 Emoji 表情 😊
            3. **禁止用语**：避免使用"根据上下文"、"所提供的信息"等生硬表述
            
            **回答范围判断**：
            - ✅ 如果用户问题与上下文信息直接相关，请提供详细、准确的回答
            - ✅ 如果用户问题与上下文信息间接相关，可以基于已有信息进行合理推断
            - ❌ 如果用户问题完全超出上下文范围，或者上下文信息不足以回答该问题
        
            **无法回答时的统一回复**：
            当遇到以下情况时，请统一回复：
            "此问题暂时无法回答，可以加博主「小哈」的私人微信来咨询，记得添加时一定要备注 **星球咨询**"
            微信二维码：![](https://img.quanxiaoha.com/quanxiaoha/173950927165691)
        
            **图片展示**：
            如需要展示图片，请使用 Markdown 格式：![](图片链接)
        
            现在请根据以上要求回答问题。
            """);

    public CustomerServiceAdvisor(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        // 获取用户输入的提示词
        Prompt prompt = chatClientRequest.prompt();
        UserMessage userMessage = prompt.getUserMessage();

        // 查询向量库
        // 检索与查询相似的文档
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(userMessage.getText()) // 查询的关键词
                .topK(3) // 查询相似度最高的 3 条文档
                .build());

        // 构建向量查询结果上下文信息
        String context = buildContext(documents);

        // 填充提示词占位符，转换为 Prompt 提示词对象
        Prompt newPrompt = DEFAULT_PROMPT_TEMPLATE.create(Map.of("question", userMessage.getText(),
                "context", context), chatClientRequest.prompt().getOptions());

        log.info("## 重新构建的增强提示词: {}", newPrompt.getUserMessage().getText());

        // 重新构建 ChatClientRequest，设置重新构建的 “增强提示词”
        ChatClientRequest newChatClientRequest = ChatClientRequest.builder()
                .prompt(newPrompt)
                .build();

        return streamAdvisorChain.nextStream(newChatClientRequest);
    }

    /**
     * 构建上下文
     * @param documents
     * @return
     */
    private String buildContext(List<Document> documents) {
        StringBuilder contextTemp = new StringBuilder();

        for (Document document : documents) {
            contextTemp.append(String.format("""
                        %s
                        ---\n
                        """, document.getText()));
        }

        return contextTemp.toString();
    }

    @Override
    public String getName() {
        // 获取类名称
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 1; // order 值越小，越先执行
    }
}
