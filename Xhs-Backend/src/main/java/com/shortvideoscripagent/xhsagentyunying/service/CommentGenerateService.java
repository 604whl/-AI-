package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.dto.comment.CommentGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.comment.CommentGenerateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.comment.CommentType;
import com.shortvideoscripagent.xhsagentyunying.freeze.ComplianceHit;
import com.shortvideoscripagent.xhsagentyunying.freeze.ComplianceScanner;
import com.shortvideoscripagent.xhsagentyunying.freeze.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论生成服务。
 * <p>
 * 支持 4 种场景（首问/追问/私信话术/转化话术），生成后经过合规扫描。
 * Mock 优先，真实 AI 不可用时 fallback 到 Mock。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentGenerateService {

    private final AiRuntimePolicy aiRuntimePolicy;
    private final ModelProviderRegistry modelProviderRegistry;
    private final ComplianceScanner complianceScanner;

    /**
     * 生成评论话术。
     *
     * @param request 评论生成请求
     * @return 评论生成响应
     */
    public CommentGenerateResponse generate(CommentGenerateRequest request) {
        // 1. 构建 prompt
        String systemPrompt = buildSystemPrompt(request);
        String userPrompt = buildUserPrompt(request);

        String generatedComment;
        // 2. Mock 优先或真实 AI
        if (aiRuntimePolicy.useMockResponses()) {
            log.debug("using mock response for comment generation");
            return buildMockResponse(request.getCommentType());
        }

        try {
            // 3. 调用真实 AI
            String raw = modelProviderRegistry.chatWithFallback(systemPrompt, userPrompt).content();
            generatedComment = raw.trim();
        } catch (Exception e) {
            log.warn("LLM call failed, fallback to mock: {}", e.getMessage());
            return buildMockResponse(request.getCommentType());
        }

        // 4. 合规扫描
        return scanAndBuildResponse(generatedComment);
    }

    private CommentGenerateResponse scanAndBuildResponse(String comment) {
        List<ComplianceHit> allHits = new ArrayList<>();

        // 扫描所有适用规则
        allHits.addAll(complianceScanner.scan("ABSOLUTE_CLAIM", comment));
        allHits.addAll(complianceScanner.scan("AD_LAW_RISK", comment));
        allHits.addAll(complianceScanner.scan("MEDICAL_RISK", comment));
        allHits.addAll(complianceScanner.scan("FINANCE_RISK", comment));
        allHits.addAll(complianceScanner.scan("IMMIGRATION_RISK", comment));

        RiskLevel worst = ComplianceScanner.aggregateRiskLevel(allHits);
        return buildComplianceResponse(comment, worst, allHits);
    }

    private CommentGenerateResponse buildComplianceResponse(String comment, RiskLevel riskLevel, List<ComplianceHit> hits) {
        String riskLevelStr = riskLevel.name();
        String complianceRisk = riskLevel == RiskLevel.LOW ? "LOW" : riskLevel.name();
        boolean requiresReview = riskLevel != RiskLevel.LOW;

        // BLOCKED 场景：comment 置空，但保留审核标记
        String finalComment = (riskLevel == RiskLevel.BLOCKED) ? "" : comment;

        return CommentGenerateResponse.builder()
                .comment(finalComment)
                .requiresReview(requiresReview)
                .complianceRisk(complianceRisk)
                .riskLevel(riskLevelStr)
                .hitDetails(hits.isEmpty() ? List.of() : hits)
                .build();
    }

    /**
     * Mock 响应（4 种场景各有不同示例）。
     */
    private CommentGenerateResponse buildMockResponse(CommentType type) {
        String comment = switch (type) {
            case FIRST_QUESTION -> "学姐请问澳洲留学一年要花多少钱呀？中介费怎么算？";
            case ASK_FOLLOW_UP -> "谢谢学姐回复！想问下商科的话墨大和悉大哪个更推荐呀？";
            case PRIVATE_MESSAGE -> "宝子~私信你了哦，有什么问题随时问我呀！";
            case CONVERSION -> "学姐整理了一份各校学费对比表，需要的话私信我免费发你~可以帮你做个初步选校方案";
        };

        return CommentGenerateResponse.builder()
                .comment(comment)
                .requiresReview(false)
                .complianceRisk("LOW")
                .riskLevel("LOW")
                .hitDetails(List.of())
                .build();
    }

    private String buildSystemPrompt(CommentGenerateRequest request) {
        String scenarioInstruction = switch (request.getCommentType()) {
            case FIRST_QUESTION -> "你是一个留学中介机构的官方账号，回复用户的第一条提问。语气友好专业，提供有价值的信息，不要过度承诺。";
            case ASK_FOLLOW_UP -> "你是留学中介的客服，回复用户的追问。保持上下文连贯，补充上一轮回答中没有覆盖的细节。";
            case PRIVATE_MESSAGE -> "你是一个留学中介账号，引导用户私信获取更多信息。语气亲切自然，不要过于商业化，让用户感觉被关心。";
            case CONVERSION -> "你是留学中介的转化顾问，软性引导用户咨询或留资。提供具体的价值点，避免硬广，让用户主动想了解更多信息。";
        };

        String personaInstruction = "";
        if (request.getPersona() != null && !request.getPersona().isBlank()) {
                personaInstruction = switch (request.getPersona().toLowerCase()) {
                    case "agency" -> "以品牌 / 机构专业顾问的口吻回复。";
                    case "mentor" -> "以创作者 / IP 分享经验的口吻回复，亲切自然。";
                    case "senior" -> "以资深用户分享心得的口吻回复，稳重可信。";
                    default -> "";
            };
        }

        int maxLen = request.getMaxLength() != null ? request.getMaxLength() : 200;
        String lengthInstruction = "回复控制在 " + maxLen + " 字以内。";
        String absoluteBan = "禁止使用任何绝对化承诺词汇（如：100%、绝对、包过、保证、稳过等）。";

        return scenarioInstruction + "\n\n" + personaInstruction + "\n\n" + lengthInstruction + "\n" + absoluteBan;
    }

    private String buildUserPrompt(CommentGenerateRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下笔记内容，生成一条合适的评论回复：\n\n");

        if (request.getPlatform() != null && !request.getPlatform().isBlank()) {
            sb.append("平台：").append(request.getPlatform()).append("\n");
        }

        if (request.getDraftContext() != null && !request.getDraftContext().isBlank()) {
            sb.append("笔记内容：\n").append(request.getDraftContext()).append("\n");
        }

        sb.append("\n请直接输出评论内容，不要添加额外说明。");

        return sb.toString();
    }
}