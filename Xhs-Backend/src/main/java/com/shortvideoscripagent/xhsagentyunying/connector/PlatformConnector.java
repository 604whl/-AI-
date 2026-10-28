package com.shortvideoscripagent.xhsagentyunying.connector;

import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformConnectorLevel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 阶段 3 S3-1 平台连接器统一抽象。
 * <p>
 * 所有真实数据回流（发布 / 指标 / 热点 / 竞品）都必须经由本接口，
 * 严禁在业务 Service 中直接散落平台 SDK 调用。
 * <p>
 * 连接器等级复用阶段 0 冻结的 {@link PlatformConnectorLevel}，不得新增等级。
 * <p>
 * <b>实现约束</b>：
 * <ul>
 *   <li>不得伪造外部内容 ID：无法自动发布的场景返回
 *       {@link PublishResult#manualRequired()}。</li>
 *   <li>无真实外部源的数据拉取返回空列表，不抛异常、不编造数据。</li>
 *   <li>不得写死密码 / Token。</li>
 * </ul>
 */
public interface PlatformConnector {

    /**
     * 连接器实现代码，例如 {@code XHS_BROWSER_SIDEBAR}。
     */
    String providerCode();

    /**
     * 平台代码，例如 {@code XIAOHONGSHU}。
     */
    String platformCode();

    /**
     * 连接器等级（复用 freeze 包既有枚举）。
     */
    PlatformConnectorLevel level();

    /**
     * 发布内容。
     */
    PublishResult publish(PublishRequest request);

    /**
     * 拉取指标快照。
     */
    List<MetricSample> fetchMetrics(MetricFetchRequest request);

    /**
     * 拉取热点选题。
     */
    List<HotTopicSample> fetchHotTopics(HotTopicFetchRequest request);

    /**
     * 拉取竞品笔记。
     */
    List<CompetitorNoteSample> fetchCompetitorNotes(CompetitorFetchRequest request);

    /**
     * 健康检查（只读，不产生副作用）。
     */
    ConnectorHealthStatus healthCheck();
}
