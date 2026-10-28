package com.shortvideoscripagent.xhsagentyunying.dto.connector;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 连接器诊断结果。
 * <p>
 * 诊断是<b>只读、无副作用</b>的操作：绝不触发真实发布，绝不写入业务数据。
 */
@Data
@Builder
public class ConnectorDiagnoseResponse {

    /** HEALTHY / DEGRADED / UNAVAILABLE / NOT_AUTHORIZED */
    private String status;

    /** 逐项检查结果 */
    private List<CheckResult> checks;

    /** 降级目标 provider code */
    private String degradedTo;

    /** 检查完成时间 */
    private OffsetDateTime checkedAt;

    /**
     * 单项检查结果。
     */
    @Data
    @Builder
    public static class CheckResult {
        /** 检查项名称 */
        private String name;
        /** 是否通过 */
        private Boolean passed;
        /** 详情说明 */
        private String detail;
    }
}
