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
 * @Description: AI 客服问答文件存储
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ai_customer_service_file_storage")
public class AiCustomerServiceFileStorageDO {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 文件 MD5 值，用于秒传、断点续传与分片归属 */
    private String fileMd5;
    /** 文件原始名称 */
    private String fileName;
    /** 文件在服务器上的存储路径 */
    private String filePath;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 总分片数 */
    private Integer totalChunks;
    /** 已上传分片数 */
    private Integer uploadedChunks;
    /** 状态：0-上传中 1-待处理 2-向量化中 3-已完成 4-失败 */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
