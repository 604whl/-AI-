package com.shortvideoscripagent.xhsagentyunying.template;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Template;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TemplateApplyLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TemplateApplyLogMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TemplateMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.template.TemplateApplyStatsResponse;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateStatsService {

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_FORBIDDEN = 40301;

    private final TemplateMapper templateMapper;
    private final TemplateApplyLogMapper applyLogMapper;
    private final WorkspaceService workspaceService;

    /**
     * 单模板效果统计（30天引用次数 + 采用率）。
     * 采用率 = published 资产数 / 引用次数
     */
    public TemplateApplyStatsResponse getApplyStats(java.util.UUID templateId) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) throw new BusinessException(CODE_NOT_FOUND, "template_not_found");

        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);

        // 引用次数（按日聚合）
        List<com.shortvideoscripagent.xhsagentyunying.domain.mapper.TemplateApplyLogMapper.DailyStat> rawStats =
                applyLogMapper.selectDailyStats(templateId, from);
        List<TemplateApplyStatsResponse.DailyStat> dailyStats = rawStats.stream()
                .map(s -> TemplateApplyStatsResponse.DailyStat.builder()
                        .day(s.getDay())
                        .count(s.getCount())
                        .build())
                .toList();

        // 总引用次数
        long applyCount = applyLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TemplateApplyLog>()
                        .eq(TemplateApplyLog::getTemplateId, templateId));

        // 采用率：JOIN content_asset WHERE status = published
        long adoptionCount = templateMapper.selectAdoptionCount(templateId);
        double adoptionRate = applyCount > 0 ? (double) adoptionCount / applyCount * 100.0 : 0.0;

        return TemplateApplyStatsResponse.builder()
                .templateId(templateId.toString())
                .totalCount(applyCount)
                .adoptionCount(adoptionCount)
                .adoptionRate(Math.round(adoptionRate * 100.0) / 100.0)
                .dailyStats(dailyStats)
                .build();
    }

    /**
     * 热门模板排行榜（按30天引用次数）。
     * 返回当前用户有权访问的模板，按引用次数降序。
     */
    public List<TemplateLeaderboardEntry> getLeaderboard(Long userId, int limit) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        java.util.UUID wsUuid = java.util.UUID.fromString(workspaceId);

        // 校验用户角色（至少 VIEWER 可看）
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);

        List<TemplateMapper.LeaderboardEntry> entries = templateMapper.selectLeaderboard(wsUuid, from, safeLimit);
        return entries.stream()
                .map(e -> TemplateLeaderboardEntry.builder()
                        .templateId(e.getTemplateId().toString())
                        .title(e.getTitle())
                        .applyCount30d(e.getApplyCount30d())
                        .adoptionRate(e.getAdoptionRate())
                        .build())
                .toList();
    }
}