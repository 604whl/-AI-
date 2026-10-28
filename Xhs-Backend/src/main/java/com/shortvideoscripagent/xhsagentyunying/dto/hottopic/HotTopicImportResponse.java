package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S3-9 热点手动导入响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicImportResponse {

    /** 成功导入条数 */
    private Integer imported;

    /** 跳过条数（重复 / 字段缺失） */
    private Integer skipped;

    /** 是否处于降级状态 */
    private Boolean degraded;

    /** 导入日志 ID（CSV 形态才有） */
    private Long importLogId;

    /** 错误行数 */
    private Integer errorRows;

    /** 错误明细（行号 + 原因） */
    private java.util.List<String> errors;

    /** 采集是否处于降级状态（外部源不可用，导入即兜底路径） */
    private Boolean fallbackUsed;
}
