package com.shortvideoscripagent.xhsagentyunying.billing.statement;

import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.pricing.BillingDtos;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * S4-3 管理端成本毛利服务。
 * <p>
 * 按<b>工作区 / 套餐 / 时间段</b>聚合收入与成本，毛利 = 收入 − 成本。
 * 成本侧按台账的 {@code price_version} 回查 {@code app.price_book.cost_per_unit}，
 * 保证改价后历史成本仍按当时价格版本计算（误差可解释）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMarginService {

    private final StatementService statementService;
    private final WorkspaceMapper workspaceMapper;

    /**
     * 聚合成本毛利。
     *
     * @param workspaceId 可为空；为空时聚合全平台
     * @param from        起始时间（含），为空默认一个月前
     * @param to          结束时间（不含），为空默认当前
     */
    public BillingDtos.MarginView aggregateMargin(String workspaceId,
                                                   OffsetDateTime from,
                                                   OffsetDateTime to) {
        OffsetDateTime safeFrom = from == null ? OffsetDateTime.now().minusMonths(1) : from;
        OffsetDateTime safeTo = to == null ? OffsetDateTime.now() : to;

        List<UsageLedgerMapper.WorkspaceCostRow> rows =
                statementService.aggregateCostByWorkspace(workspaceId, safeFrom, safeTo);

        BillingDtos.MarginView view = new BillingDtos.MarginView();
        view.setFrom(safeFrom);
        view.setTo(safeTo);
        view.setCurrency(StatementService.DEFAULT_CURRENCY);

        BigDecimal revenue = BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP);
        BigDecimal cost = BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP);
        for (UsageLedgerMapper.WorkspaceCostRow row : rows) {
            BillingDtos.WorkspaceMarginRow dto = new BillingDtos.WorkspaceMarginRow();
            dto.setWorkspaceId(row.getWorkspaceId());
            dto.setPlanCode(planCodeOf(row.getWorkspaceId()));
            dto.setQuantity(BillingMath.scale(row.getQuantity()));
            dto.setRevenue(BillingMath.scale(row.getRevenue()));
            dto.setCost(BillingMath.scale(row.getCost()));
            BigDecimal margin = BillingMath.scale(row.getRevenue().subtract(row.getCost()));
            dto.setMargin(margin);
            dto.setMarginRate(marginRate(row.getRevenue(), margin));
            view.getRows().add(dto);
            revenue = revenue.add(dto.getRevenue());
            cost = cost.add(dto.getCost());
        }
        view.setTotalRevenue(BillingMath.scale(revenue));
        view.setTotalCost(BillingMath.scale(cost));
        view.setTotalMargin(BillingMath.scale(revenue.subtract(cost)));
        return view;
    }

    /**
     * 毛利率（%）。收入为 0 时返回 null（无法定义）。
     */
    private static BigDecimal marginRate(BigDecimal revenue, BigDecimal margin) {
        if (revenue == null || revenue.signum() == 0) {
            return null;
        }
        return margin.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP);
    }

    /**
     * 取工作区当前套餐编码（{@code app.workspace.plan_code} 冗余列）。
     */
    private String planCodeOf(String workspaceId) {
        if (workspaceId == null) {
            return null;
        }
        try {
            Workspace ws = workspaceMapper.selectById(workspaceId);
            return ws == null ? null : ws.getPlanCode();
        } catch (Exception ex) {
            log.debug("Failed to read plan_code for ws={}: {}", workspaceId, ex.getMessage());
            return null;
        }
    }
}
