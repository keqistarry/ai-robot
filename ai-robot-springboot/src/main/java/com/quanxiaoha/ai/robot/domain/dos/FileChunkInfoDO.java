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
 * @Description: 分片信息表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_file_chunk_info")
public class FileChunkInfoDO {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 文件 MD5 值，关联同一文件的所有分片 */
    private String fileMd5;
    /** 分片序号（从 1 开始） */
    private Integer chunkNumber;
    /** 该分片在服务器上的存储路径 */
    private String chunkPath;
    /** 该分片大小（字节） */
    private Long chunkSize;
    /** 创建时间 */
    private LocalDateTime createTime;
}
