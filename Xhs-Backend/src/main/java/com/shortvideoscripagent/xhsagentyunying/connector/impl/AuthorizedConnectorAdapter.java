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
 * 第三方授权连接器适配器（等级 {@code AUTHORIZED_CONNECTOR}）。
 * <p>
 * 适配经授权的第三方数据服务。当前阶段尚未接入任何真实第三方服务，
 * 因此数据拉取返回空列表，发布返回需人工确认语义。
 */
@Slf4j
@Component
public class AuthorizedConnectorAdapter implements PlatformConnector {

    public static final String PROVIDER_CODE = "XHS_AUTHORIZED_CONNECTOR";

    private static final String PLATFORM_CODE = "XIAOHONGSHU";

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
        return PlatformConnectorLevel.AUTHORIZED_CONNECTOR;
    }

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
