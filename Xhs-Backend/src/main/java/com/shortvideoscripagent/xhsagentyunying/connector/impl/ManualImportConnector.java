package com.shortvideoscripagent.xhsagentyunying.connector.impl;

import com.shortvideoscripagent.xhsagentyunying.connector.CompetitorFetchRequest;
import com.shortvideoscripagent.xhsagentyunying.connector.CompetitorNoteSample;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorHealthStatus;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorHealthStatusEnum;
import com.shortvideoscripagent.xhsagentyunying.connector.HotTopicFetchRequest;
import com.shortvideoscripagent.xhsagentyunying.connector.HotTopicSample;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricFetchRequest;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricSample;
import com.shortvideoscripagent.xhsagentyunying.connector.PlatformConnector;
import com.shortvideoscripagent.xhsagentyunying.connector.PublishRequest;
import com.shortvideoscripagent.xhsagentyunying.connector.PublishResult;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformConnectorLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 手动导入连接器（等级 {@code MANUAL_IMPORT}）。
 * <p>
 * 数据来源完全由用户手动上传（CSV / Excel / 截图 / 链接），系统不代为操作平台。
 * <p>
 * <b>关键约束</b>：{@link #publish(PublishRequest)} 必须返回
 * {@link PublishResult#manualRequired()} —— {@code manual=true} 且
 * {@code externalContentId=null}，严禁伪造外部内容 ID。
 */
@Slf4j
@Component
public class ManualImportConnector implements PlatformConnector {

    public static final String PROVIDER_CODE = "DOUYIN_CSV_IMPORT";

    private static final String PLATFORM_CODE = "DOUYIN";

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String platformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public PlatformConnectorLevel level() {
        return PlatformConnectorLevel.MANUAL_IMPORT;
    }

    /**
     * 手动导入不支持自动发布。
     */
    @Override
    public PublishResult publish(PublishRequest request) {
        return PublishResult.manualRequired();
    }

    @Override
    public List<MetricSample> fetchMetrics(MetricFetchRequest request) {
        return List.of();
    }

    @Override
    public List<HotTopicSample> fetchHotTopics(HotTopicFetchRequest request) {
        return List.of();
    }

    @Override
    public List<CompetitorNoteSample> fetchCompetitorNotes(CompetitorFetchRequest request) {
        return List.of();
    }

    @Override
    public ConnectorHealthStatus healthCheck() {
        long start = System.nanoTime();
        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
        return new ConnectorHealthStatus(
                ConnectorHealthStatusEnum.HEALTHY.name(),
                null,
                OffsetDateTime.now(),
                null,
                null,
                0,
                null,
                latencyMs);
    }
}
