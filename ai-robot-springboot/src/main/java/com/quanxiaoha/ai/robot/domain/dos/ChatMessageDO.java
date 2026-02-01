package com.quanxiaoha.ai.robot.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Description: 聊天消息 DO 实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_chat_message")
public class ChatMessageDO {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属对话的 UUID，关联 t_chat.uuid */
    private String chatUuid;
    /** 消息正文（用户输入或 AI 回复的文本） */
    private String content;
    /** 模型推理过程内容（若模型支持，如 DeepSeek 思考链） */
    private String reasoningContent;
    /** 角色：user-用户 assistant-AI 助手 */
    private String role;
    /** 创建时间 */
    private LocalDateTime createTime;
}
