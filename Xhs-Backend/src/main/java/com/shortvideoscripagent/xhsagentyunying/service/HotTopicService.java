package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicImportResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicListResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicPromoteResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicRefreshRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicRefreshResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicSamplesResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicTrendResponse;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * S3-9 热点数据产品化服务。
 * <p>
 * 核心约束：
 * <ul>
 *   <li><b>禁止伪装实时</b>：静态种子 {@code source_type='SEED'} + {@code is_realtime=FALSE}；
 *       联网搜索采集的才是 {@code SEARCH_TOOL} + {@code is_realtime=TRUE}</li>
 *   <li><b>去重保留历史</b>：同平台 + 归一化标题只保留最新一条 {@code status='active'}，
 *       其余标 {@code status='superseded'}（不删除，供趋势对比）</li>
 *   <li><b>缓存</b>：同窗口命中 {@code app.hot_topic_cache} 不重复调用外部源</li>
 *   <li><b>降级可见</b>：采集失败返回 {@code degraded=true} + 原因 + 兜底路径，
 *       不允许静默展示陈旧数据</li>
 *   <li><b>可做方向有证据</b>：每条建议必须引用真实标题 + 来源 + 采集时间</li>
 *   <li><b>选题池透传</b>：{@code source_name} / {@code confidence} / {@code source_fetched_at}</li>
 * </ul>
 */
public interface HotTopicService {

    // ══════════════════════════════════════════════════════════════════
    // 采集
    // ══════════════════════════════════════════════════════════════════

    /**
     * 单次拉取（调度器 / 旧调用方）：调用联网搜索拉新一批，写入 hot_topic 表。
     * <p>
     * 写入的记录一律 {@code source_type='SEARCH_TOOL'} + {@code source_name='web_search'}
     * + {@code is_realtime=TRUE}；命中同窗口缓存时不重复调用外部源。
     * </p>
     *
     * @return 本次写入的记录数
     */
    int refreshFromAgentTool();

    /**
     * 手动触发一次采集（ADMIN/OWNER）。
     * <p>
     * 走授权门禁：平台不在授权矩阵内 → 403 {@code CONNECTOR_NOT_AUTHORIZED}。
     * 采集失败 → 写缓存降级标记并返回 {@code degraded=true}，不抛异常掩盖失败。
     * </p>
     */
    HotTopicRefreshResponse refresh(String workspaceId, Long userId, HotTopicRefreshRequest request);

    // ══════════════════════════════════════════════════════════════════
    // 查询
    // ══════════════════════════════════════════════════════════════════

    /**
     * 分页查询热门话题列表（含来源 / 可信度 / 是否实时 / 降级状态）。
     *
     * @param sourceType 来源类型过滤（不传表示不限）
     */
    HotTopicListResponse list(String platform, String category, String sourceType,
                              Boolean promoted, int page, int size);

    /**
     * 查询某热点的历史样本（同平台 + 同归一化标题的多条快照），供趋势对比。
     */
    HotTopicSamplesResponse samples(Long hotTopicId);

    /**
     * 计算指定话题在时间窗口内的热度趋势。
     * <p>
     * 趋势输入必须是同平台 + 同归一化标题的多条快照；
     * 单条记录不得输出"上升"（{@code points.size() < 3 → "new"}）。
     * </p>
     *
     * @param title      原始标题（会规范化后匹配）
     * @param windowDays 时间窗口天数（1-30）
     */
    HotTopicTrendResponse computeTrend(String title, int windowDays);

    // ══════════════════════════════════════════════════════════════════
    // 手动导入
    // ══════════════════════════════════════════════════════════════════

    /**
     * 手动导入热点（表单 / JSON）。
     * <p>
     * 导入项一律：{@code source_type='MANUAL_IMPORT'} + {@code confidence='LOW'}
     * + {@code is_realtime=FALSE}。
     * </p>
     */
    HotTopicImportResponse importItems(String workspaceId, Long userId, HotTopicImportRequest request);

    /**
     * CSV 导入热点。
     * <p>
     * 表头白名单校验：未知列直接拒绝 → 400 {@code CSV_SCHEMA_INVALID}。
     * </p>
     *
     * @return 导入结果（含 importLogId）
     */
    HotTopicImportResponse importCsv(String workspaceId, Long userId, String filename, String csvText,
                                     List<String> errorSink);

    // ══════════════════════════════════════════════════════════════════
    // 选题池提升
    // ══════════════════════════════════════════════════════════════════

    /**
     * 将指定热点提升到选题池（EDITOR+），透传来源三列。
     */
    HotTopicPromoteResponse promoteToTopicPool(String workspaceId, Long userId, Long hotTopicId,
                                               String category, List<String> tags);

    /**
     * 批量将热门话题提升（写入）到 topic_pool（既有调度调用）。
     */
    int promoteToTopicPool(String workspaceId, Long userId, int maxCount);

    // ══════════════════════════════════════════════════════════════════
    // 内部 DTO（服务层内部使用）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 服务层内部热点视图。
     */
    record HotTopicDto(
            Long id,
            String platform,
            String category,
            String title,
            Integer heatScore,
            OffsetDateTime fetchedAt,
            Boolean promotedToPool,
            String sourceName,
            String sourceType,
            String confidence,
            Boolean isRealtime,
            String sourceUrl,
            Boolean degraded,
            String degradedReason,
            String status,
            String suggestedAngles,
            String trend
    ) {
    }

    /**
     * 分页结果。
     */
    record PageResult<T>(List<T> items, long total, int page, int size) {
    }
}
