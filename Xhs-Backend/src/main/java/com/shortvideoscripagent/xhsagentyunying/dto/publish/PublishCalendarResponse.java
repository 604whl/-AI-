package com.shortvideoscripagent.xhsagentyunying.dto.publish;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 排期日历视图（按天分组）。
 * <p>
 * 验收契约：{@code {days: [{date, slots: [{scheduleId, platformCode, status}]}]}}
 */
@Data
@Builder
public class PublishCalendarResponse {

    /** 按日期升序排列的天分组 */
    private List<PublishCalendarDay> days;

    /**
     * 单日分组。
     */
    @Data
    @Builder
    public static class PublishCalendarDay {

        /** 日期（yyyy-MM-dd，按排期时区归一后的本地日期） */
        private String date;

        /** 当天的排期时段 */
        private List<PublishCalendarSlot> slots;
    }

    /**
     * 单个时段占用。
     */
    @Data
    @Builder
    public static class PublishCalendarSlot {

        private String scheduleId;

        private String assetId;

        private String title;

        private String platformCode;

        private String accountId;

        private String status;

        /** 该时段在排期时区下的本地时间 */
        private String localTime;
    }
}
