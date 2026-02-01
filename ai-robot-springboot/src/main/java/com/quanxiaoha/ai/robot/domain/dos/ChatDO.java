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
 * @Description: 对话 DO 实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_chat")
public class ChatDO {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对话唯一标识，前端/业务使用 */
    private String uuid;
    /** 对话摘要/标题，用于侧边栏展示 */
    private String summary;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间（如重命名时更新） */
    private LocalDateTime updateTime;
}
