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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
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

        s.setAssetId(assetId);
        s.setStatus(BrowserAssistSession.STATUS_FILLED);
        s.setLastUsedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        sessionMapper.updateById(s);

        log.info("Browser assist session {} filled with asset {} by user {}", sessionId, assetId, userId);
        return toResponse(s);
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