package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.billing.plan.PlanService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.PlanDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * S4-1 套餐公开只读接口。
 * <p>
 * GET /v1/plans
 * GET /v1/plans/{code}
 * </p>
 * <p>
 * 套餐本身是<b>全局公开产品目录</b>（不属于任何 workspace 的私有数据），
 * 因此不按 workspace 隔离；真正按 workspace 隔离的是订阅接口。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/plans")
@RequiredArgsConstructor
public class PlanCatalogController {

    private final PlanService planService;

    /**
     * 公开套餐列表（每个 code 只返回最新 active 版本）。
     */
    @GetMapping
    public ApiResponse<List<PlanDtos.PlanView>> listPlans() {
        return ApiResponse.ok(planService.listPublicPlans());
    }

    /**
     * 按 code 读取套餐详情。
     */
    @GetMapping("/{code}")
    public ApiResponse<PlanDtos.PlanView> getPlan(@PathVariable String code) {
        return ApiResponse.ok(planService.getPublicPlan(code));
    }
}
