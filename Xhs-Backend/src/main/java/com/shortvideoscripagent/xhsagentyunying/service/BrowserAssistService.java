package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.BrowserAssistSession;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.BrowserAssistSessionMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.browser.BrowserSessionResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.browser.CreateSessionRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.browser.FillSessionRequest;
import com.shortvideoscripagent.xhsagentyunying.service.browser.BrowserDriver;
import com.shortvideoscripagent.xhsagentyunying.service.browser.NoopBrowserDriver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrowserAssistService {

    private static final int CODE_NOT_FOUND = 40450;
    private static final int CODE_CONFLICT = 40950;
    private static final int CODE_BAD_REQUEST = 40050;
    private static final String POLICY_NOTICE = "Browser assist requires user confirmation to publish. System is not responsible for content compliance.";

    private final BrowserAssistSessionMapper sessionMapper;
    private final ContentAssetMapper assetMapper;
    /**
     * L3：真实浏览器驱动抽象。
     * <p>
     * 用 {@link ObjectProvider} 而不是直接注入，是为了让「没有驱动」成为<b>合法状态</b>：
     * 容器里不存在任何 {@link BrowserDriver} Bean 时回退到 {@link NoopBrowserDriver}
     * （永远返回「需要人工」），而不是让应用启动失败。
     * 将来接入 Playwright / Selenium 时，只要注册一个 {@link BrowserDriver} Bean 即可自动生效。
     * </p>
     */
    private final ObjectProvider<BrowserDriver> browserDriverProvider;
    /** 用于在失败路径上独立提交 failed 状态（见 markFailed） */
    private final PlatformTransactionManager transactionManager;

    /** 解析当前生效的浏览器驱动；未注册任何驱动时退化为 Noop（诚实降级）。 */
    private BrowserDriver browserDriver() {
        BrowserDriver driver = browserDriverProvider.getIfAvailable();
        return driver != null ? driver : new NoopBrowserDriver();
    }

    public BrowserSessionResponse createSession(Long userId, CreateSessionRequest req) {
        String wsIdStr = WorkspaceContext.getWorkspaceId();
        if (wsIdStr == null || wsIdStr.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }

        BrowserAssistSession s = new BrowserAssistSession();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setWorkspaceId(wsIdStr);
        s.setPlatform(req.getPlatform());
        s.setAccountLabel(req.getAccountLabel());
        s.setStatus(BrowserAssistSession.STATUS_CREATED);
        s.setCreatedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        sessionMapper.insert(s);

        log.info("Browser assist session {} created by user {} for platform {}", s.getId(), userId, req.getPlatform());
        return toResponse(s);
    }

    public BrowserSessionResponse getSession(Long userId, UUID sessionId) {
        String wsIdStr = WorkspaceContext.getWorkspaceId();

        BrowserAssistSession s = sessionMapper.selectByIdAndWorkspace(sessionId, wsIdStr);
        if (s == null) {
            throw new BusinessException(CODE_NOT_FOUND, "session_not_found");
        }
        return toResponse(s);
    }

    /**
     * 填充会话。
     * <p>
     * <b>故意不加 {@code @Transactional}</b>：失败路径需要把会话持久化为
     * {@code failed} 状态后再抛异常。若整体包在一个事务里，异常回滚会把
     * failed 状态一起撤销，「诚实降级」语义失效。成功路径只有一次单表
     * UPDATE，本身不需要事务原子性。
     * </p>
     */
    public BrowserSessionResponse fillSession(Long userId, FillSessionRequest req) {
        String wsIdStr = WorkspaceContext.getWorkspaceId();
        UUID sessionId = UUID.fromString(req.getSessionId());
        UUID assetId = UUID.fromString(req.getAssetId());

        BrowserAssistSession s = sessionMapper.selectByIdAndWorkspace(sessionId, wsIdStr);
        if (s == null) {
            throw new BusinessException(CODE_NOT_FOUND, "session_not_found");
        }

        ContentAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !asset.getWorkspaceId().equals(wsIdStr)) {
            throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
        }

        if (!BrowserAssistSession.STATUS_CREATED.equals(s.getStatus())
                && !BrowserAssistSession.STATUS_ACTIVE.equals(s.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "session_not_fillable_status: " + s.getStatus());
        }

        // L3：真实执行浏览器填充；无驱动 / 填充失败 → 标记 failed 并要求人工，
        // 绝不置 STATUS_FILLED 伪装成功。
        BrowserDriver driver = browserDriver();
        if (!driver.isAvailable()) {
            markFailed(s, sessionId, assetId, "browser_driver_unavailable");
            throw new BusinessException(CODE_CONFLICT, "browser_manual_required");
        }

        boolean filled;
        try {
            filled = driver.fill(s, asset);
        } catch (Exception e) {
            log.warn("Browser fill failed: session={} asset={} error={}", sessionId, assetId, e.getMessage());
            filled = false;
        }
        if (!filled) {
            markFailed(s, sessionId, assetId, "browser_fill_failed");
            throw new BusinessException(CODE_CONFLICT, "browser_manual_required");
        }

        s.setAssetId(assetId);
        s.setStatus(BrowserAssistSession.STATUS_FILLED);
        s.setLastUsedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        sessionMapper.updateById(s);

        log.info("Browser assist session {} filled with asset {} by user {}", sessionId, assetId, userId);
        return toResponse(s);
    }

    /**
     * 填充失败：会话置 failed，并保留 assetId 便于人工续填。
     * <p>
     * <b>必须独立提交</b>：{@link #fillSession} 的失败路径要先把会话持久化为
     * {@code failed} 再抛异常。若状态更新与抛异常同处一个事务，Spring 回滚时
     * 会把 failed 状态一起撤销，「诚实降级」语义失效（会话永远停在 created/active，
     * 调用方看不出失败）。这里用 {@link TransactionTemplate} +
     * {@code PROPAGATION_REQUIRES_NEW} 独立提交，规避 Spring 代理自调用失效问题。
     * </p>
     */
    private void markFailed(BrowserAssistSession s, UUID sessionId, UUID assetId, String reason) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> {
            s.setAssetId(assetId);
            s.setStatus(BrowserAssistSession.STATUS_FAILED);
            s.setLastUsedAt(OffsetDateTime.now());
            s.setUpdatedAt(OffsetDateTime.now());
            sessionMapper.updateById(s);
        });
        log.info("Browser assist session {} marked failed: {}", sessionId, reason);
    }

    @Transactional
    public void closeSession(Long userId, UUID sessionId) {
        String wsIdStr = WorkspaceContext.getWorkspaceId();

        BrowserAssistSession s = sessionMapper.selectByIdAndWorkspace(sessionId, wsIdStr);
        if (s == null) {
            throw new BusinessException(CODE_NOT_FOUND, "session_not_found");
        }

        s.setStatus(BrowserAssistSession.STATUS_CLOSED);
        s.setUpdatedAt(OffsetDateTime.now());
        sessionMapper.updateById(s);

        log.info("Browser assist session {} closed by user {}", sessionId, userId);
    }

    public List<BrowserSessionResponse> listSessions(Long userId) {
        String wsIdStr = WorkspaceContext.getWorkspaceId();
        return sessionMapper.selectRecentByWorkspace(wsIdStr).stream()
                .map(this::toResponse)
                .toList();
    }

    private BrowserSessionResponse toResponse(BrowserAssistSession s) {
        return BrowserSessionResponse.builder()
                .id(s.getId())
                .platform(s.getPlatform())
                .accountLabel(s.getAccountLabel())
                .status(s.getStatus())
                .assetId(s.getAssetId())
                .cookieExpiresAt(s.getCookieExpiresAt())
                .lastUsedAt(s.getLastUsedAt())
                .createdAt(s.getCreatedAt())
                .policyNotice(POLICY_NOTICE)
                .build();
    }
}