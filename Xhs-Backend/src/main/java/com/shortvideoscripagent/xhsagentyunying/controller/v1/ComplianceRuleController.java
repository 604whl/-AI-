package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ComplianceBrandWord;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ComplianceRule;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ComplianceBrandWordMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.compliance.ComplianceBrandWordRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.compliance.ComplianceRuleDTO;
import com.shortvideoscripagent.xhsagentyunying.service.ComplianceRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 合规规则管理 Controller（S2-3: 规则 CRUD + 品牌禁用词管理）。
 * <p>
 * 挂载路径 /v1/compliance，与 ComplianceController 合并以避免路由冲突。
 */
@RestController
@RequestMapping("/v1/compliance")
@RequiredArgsConstructor
public class ComplianceRuleController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final ComplianceRuleService ruleService;
    private final ComplianceBrandWordMapper brandWordMapper;

    // ─────────────────────────────────────────────────────────────────────────────
    // 规则管理
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * GET /v1/compliance/rules — 列出所有规则（含未启用的）。
     */
    @GetMapping("/rules")
    public ApiResponse<List<ComplianceRuleDTO>> listRules() {
        List<ComplianceRule> rules = ruleService.getAllRules();
        List<ComplianceRuleDTO> dtos = new ArrayList<>();
        for (ComplianceRule r : rules) {
            dtos.add(toDTO(r));
        }
        return ApiResponse.ok(dtos);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 品牌禁用词管理
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * POST /v1/compliance/brand-words — 新增品牌禁用词。
     */
    @PostMapping("/brand-words")
    public ApiResponse<ComplianceBrandWord> addBrandWord(@Valid @RequestBody ComplianceBrandWordRequest req) {
        Long userId = requireUserId();
        String wsId = WorkspaceContext.getWorkspaceId();
        UUID wsUuid = UUID.fromString(wsId);
        ComplianceBrandWord w = new ComplianceBrandWord();
        w.setWorkspaceId(wsUuid);
        w.setRuleCode(req.getRuleCode());
        w.setKeyword(req.getKeyword());
        w.setRiskLevel(req.getRiskLevel() != null ? req.getRiskLevel() : "HIGH");
        w.setCreatedBy(userId);
        w.setCreatedAt(OffsetDateTime.now());
        brandWordMapper.insert(w);
        return ApiResponse.ok(w);
    }

    /**
     * GET /v1/compliance/brand-words — 查询当前 workspace 的品牌禁用词。
     */
    @GetMapping("/brand-words")
    public ApiResponse<List<ComplianceBrandWord>> listBrandWords() {
        String wsId = WorkspaceContext.getWorkspaceId();
        UUID wsUuid = UUID.fromString(wsId);
        return ApiResponse.ok(brandWordMapper.selectByWorkspace(wsUuid));
    }

    /**
     * DELETE /v1/compliance/brand-words/{id} — 删除品牌禁用词。
     */
    @DeleteMapping("/brand-words/{id}")
    public ApiResponse<Void> deleteBrandWord(@PathVariable Long id) {
        brandWordMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 内部工具
    // ─────────────────────────────────────────────────────────────────────────────

    private ComplianceRuleDTO toDTO(ComplianceRule r) {
        ComplianceRuleDTO dto = new ComplianceRuleDTO();
        dto.setId(r.getId());
        dto.setCode(r.getCode());
        dto.setDisplayName(r.getDisplayName());
        dto.setCategoryCode(r.getCategoryCode());
        dto.setRiskLevel(r.getRiskLevel());
        dto.setDescription(r.getDescription());
        dto.setDetectorType(r.getDetectorType());
        dto.setEnabled(r.getEnabled());
        try {
            if (r.getDetectorConfig() != null) {
                if (r.getDetectorConfig().has("words")) {
                    List<String> kw = new ArrayList<>();
                    r.getDetectorConfig().get("words").forEach(e -> kw.add(e.asText()));
                    dto.setKeywords(kw);
                }
                if (r.getDetectorConfig().has("patterns")) {
                    List<String> pat = new ArrayList<>();
                    r.getDetectorConfig().get("patterns").forEach(e -> pat.add(e.asText()));
                    dto.setPatterns(pat);
                }
            }
        } catch (Exception ignored) { }
        return dto;
    }

    private Long requireUserId() {
        Long uid = RequestContext.getUserId();
        if (uid == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return uid;
    }
}