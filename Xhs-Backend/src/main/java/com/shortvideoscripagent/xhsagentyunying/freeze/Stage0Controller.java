package com.shortvideoscripagent.xhsagentyunying.freeze;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段 0：产品冻结只读元接口。
 * <p>
 * 提供：
 * <ul>
 *   <li>GET /api/v1/stage0/platforms — 平台 × 连接等级矩阵</li>
 *   <li>GET /api/v1/stage0/categories — 品类 × 风险级别</li>
 *   <li>GET /api/v1/stage0/billing-units — 计费计量单位</li>
 *   <li>GET /api/v1/stage0/state-machine — 内容状态机转移图</li>
 *   <li>GET /api/v1/stage0/compliance-rules — 激活合规规则清单</li>
 * </ul>
 * <p>
 * 只读，要求 Owner/Admin 角色（防止员工任意查看冻结矩阵）。
 */
@RestController
@RequestMapping("/api/v1/stage0")
public class Stage0Controller {

    private final Stage0Service stage0Service;

    public Stage0Controller(Stage0Service stage0Service) {
        this.stage0Service = stage0Service;
    }

    @GetMapping("/platforms")
    public ApiResponse<List<PlatformEntity>> listPlatforms() {
        return ApiResponse.ok(stage0Service.listPlatforms());
    }

    @GetMapping("/billing-units")
    public ApiResponse<List<Map<String, String>>> billingUnits() {
        List<Map<String, String>> list = new java.util.ArrayList<>();
        for (BillableUnit unit : BillableUnit.values()) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("code", unit.name());
            map.put("displayName", unit.getDisplayName());
            list.add(map);
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/billing-events")
    public ApiResponse<List<String>> billingEvents() {
        List<String> list = new java.util.ArrayList<>();
        for (BillableEvent event : BillableEvent.values()) {
            list.add(event.name());
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/risk-levels")
    public ApiResponse<List<Map<String, Object>>> riskLevels() {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (RiskLevel level : RiskLevel.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("code", level.name());
            map.put("blocksPublication", level.blocksPublication());
            map.put("requiresManualReview", level.requiresManualReview());
            list.add(map);
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/state-machine")
    public ApiResponse<Map<String, Object>> stateMachine() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> states = new java.util.ArrayList<>();
        for (ContentStatus status : ContentStatus.values()) {
            states.add(status.name());
        }
        result.put("states", states);

        List<Map<String, String>> transitions = new java.util.ArrayList<>();
        for (ContentStatus from : ContentStatus.values()) {
            for (ContentStatus to : ContentStatus.values()) {
                if (ContentStatus.canTransition(from, to)) {
                    Map<String, String> t = new LinkedHashMap<>();
                    t.put("from", from.name());
                    t.put("to", to.name());
                    transitions.add(t);
                }
            }
        }
        result.put("transitions", transitions);
        return ApiResponse.ok(result);
    }
}
