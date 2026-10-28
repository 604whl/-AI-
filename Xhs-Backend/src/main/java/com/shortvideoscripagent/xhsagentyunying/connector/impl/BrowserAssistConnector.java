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
 * 浏览器辅助连接器（等级 {@code BROWSER_ASSIST}）。
 * <p>
 * 系统负责在平台页面填充内容，<b>但绝不自动点击发布</b>；
 * 只能在用户于平台页面确认后由用户完成最终发布动作。
 * 因此 {@link #publish(PublishRequest)} 永远返回"需用户确认"语义，
 * 且不返回任何外部内容 ID。
 */
@Slf4j
@Component
public class BrowserAssistConnector implements PlatformConnector {

    public static final String PROVIDER_CODE = "XHS_BROWSER_SIDEBAR";

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
        return PlatformConnectorLevel.BROWSER_ASSIST;
    }

    /**
     * 不自动发布：仅返回需用户在平台页面确认的语义。
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
