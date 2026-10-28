package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CompetitorAccount;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CompetitorNote;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.CompetitorAccountMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.CompetitorNoteMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorAccountCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorAccountCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorAccountListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorNoteCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorNoteCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorNoteListItem;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * S3-10 竞品库服务（竞品账号 + 竞品笔记）。
 * <p>
 * <b>核心约束</b>：竞品笔记的 {@code evidenceText}（真实原文片段引用）<b>必填</b>，
 * 为空一律拒绝入库 —— 服务层 400 EVIDENCE_TEXT_REQUIRED + 数据库 CHECK 约束双重保证。
 * 没有原文片段的"竞品数据"不可复现、不可核对，等于没有证据。
 * </p>
 * <p>
 * 采集方式（{@code sourceType}）与采集时间（{@code capturedAt}）必须暴露，
 * 竞品数据的来源与新鲜度对用户可见。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorService {

    // ── 错误码 ──────────────────────────────────────────────────────────
    /** 400 竞品参数非法（含 evidenceText 缺失） */
    public static final int CODE_COMPETITOR_INVALID = 40076;
    /** 404 竞品账号不存在 / 越权 */
    public static final int CODE_COMPETITOR_ACCOUNT_NOT_FOUND = 40474;
    /** 409 竞品账号已存在 */
    public static final int CODE_COMPETITOR_DUPLICATE = 40974;

    private final CompetitorAccountMapper accountMapper;
    private final CompetitorNoteMapper noteMapper;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════════
    // 竞品账号
    // ══════════════════════════════════════════════════════════════════

    /**
     * 创建竞品账号。
     */
    @Transactional
    public CompetitorAccountCreateResponse createAccount(String workspaceId, Long userId,
                                                         CompetitorAccountCreateRequest request) {
        requireWorkspace(workspaceId);

        String platformCode = upper(request.getPlatformCode());
        if (platformCode == null) {
            throw new BusinessException(CODE_COMPETITOR_INVALID,
                    "COMPETITOR_INVALID: platform_code_required");
        }
        String accountName = request.getAccountName() == null ? null : request.getAccountName().trim();
        if (accountName == null || accountName.isEmpty()) {
            throw new BusinessException(CODE_COMPETITOR_INVALID,
                    "COMPETITOR_INVALID: account_name_required");
        }
        if (accountMapper.existsByWorkspacePlatformName(workspaceId, platformCode, accountName)) {
            throw new BusinessException(CODE_COMPETITOR_DUPLICATE,
                    "COMPETITOR_DUPLICATE: account_already_exists");
        }

        OffsetDateTime now = OffsetDateTime.now();
        CompetitorAccount account = new CompetitorAccount();
        account.setId(UUID.randomUUID());
        account.setWorkspaceId(workspaceId);
        account.setPlatformCode(platformCode);
        account.setAccountName(accountName);
        account.setAccountUrl(blankToNull(request.getAccountUrl()));
        account.setExternalAccountId(blankToNull(request.getExternalAccountId()));
        account.setFollowerCount(request.getFollowerCount());
        account.setCategory(blankToNull(request.getCategory()));
        account.setTags(request.getTags() == null ? new ArrayList<>() : new ArrayList<>(request.getTags()));
        account.setStatus(CompetitorAccount.STATUS_ACTIVE);
        account.setAddedBy(userId);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountMapper.insert(account);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("accountId", account.getId().toString());
        meta.put("platformCode", platformCode);
        meta.put("accountName", accountName);
        auditWriter.record("competitor.account.create", "COMPETITOR_ACCOUNT",
                account.getId().toString(), meta);

        log.info("Competitor account created: id={} ws={} platform={} name={}",
                account.getId(), workspaceId, platformCode, accountName);
        return CompetitorAccountCreateResponse.builder()
                .id(account.getId().toString())
                .status(account.getStatus())
                .platformCode(account.getPlatformCode())
                .accountName(account.getAccountName())
                .build();
    }

    /**
     * 分页列出竞品账号（含笔记数聚合）。
     */
    public PaginatedResponse<CompetitorAccountListItem> listAccounts(String workspaceId,
                                                                       String platformCode,
                                                                       String category,
                                                                       String status,
                                                                       int page, int size) {
        requireWorkspace(workspaceId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (long) (safePage - 1) * safeSize;

        String platform = upper(platformCode);
        String cat = blankToNull(category);
        String st = blankToNull(status);

        List<CompetitorAccount> accounts = accountMapper.listByWorkspace(workspaceId, platform, cat, st,
                offset, safeSize);

        // 批量取笔记数（避免 N+1）
        Map<UUID, Long> noteCounts = new LinkedHashMap<>();
        if (!accounts.isEmpty()) {
            List<UUID> ids = accounts.stream().map(CompetitorAccount::getId).toList();
            for (CompetitorNoteMapper.NoteCountRow row : noteMapper.countByAccounts(workspaceId, ids)) {
                noteCounts.put(row.getCompetitorAccountId(), row.getNoteCount());
            }
        }

        List<CompetitorAccountListItem> items = accounts.stream()
                .map(a -> CompetitorAccountListItem.builder()
                        .id(a.getId().toString())
                        .platformCode(a.getPlatformCode())
                        .accountName(a.getAccountName())
                        .accountUrl(a.getAccountUrl())
                        .followerCount(a.getFollowerCount())
                        .category(a.getCategory())
                        .tags(a.getTags())
                        .noteCount(noteCounts.getOrDefault(a.getId(), 0L))
                        .status(a.getStatus())
                        .updatedAt(a.getUpdatedAt())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        long total = accountMapper.countByWorkspace(workspaceId, platform, cat, st);
        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    /**
     * 查询竞品账号详情。
     */
    public CompetitorAccountListItem getAccount(String workspaceId, UUID accountId) {
        requireWorkspace(workspaceId);
        CompetitorAccount account = accountMapper.selectByIdAndWorkspace(accountId, workspaceId);
        if (account == null) {
            throw new BusinessException(CODE_COMPETITOR_ACCOUNT_NOT_FOUND, "COMPETITOR_ACCOUNT_NOT_FOUND");
        }
        long noteCount = noteMapper.countByAccount(accountId, workspaceId);
        return CompetitorAccountListItem.builder()
                .id(account.getId().toString())
                .platformCode(account.getPlatformCode())
                .accountName(account.getAccountName())
                .accountUrl(account.getAccountUrl())
                .followerCount(account.getFollowerCount())
                .category(account.getCategory())
                .tags(account.getTags())
                .noteCount(noteCount)
                .status(account.getStatus())
                .updatedAt(account.getUpdatedAt())
                .createdAt(account.getCreatedAt())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    // 竞品笔记
    // ══════════════════════════════════════════════════════════════════

    /**
     * 新增竞品笔记。
     * <p>
     * <b>evidenceText 必填</b>：没有真实原文片段引用一律拒绝入库（400 EVIDENCE_TEXT_REQUIRED）。
     * </p>
     */
    @Transactional
    public CompetitorNoteCreateResponse createNote(String workspaceId, Long userId,
                                                    UUID accountId,
                                                    CompetitorNoteCreateRequest request) {
        requireWorkspace(workspaceId);
        if (accountId == null) {
            throw new BusinessException(CODE_COMPETITOR_ACCOUNT_NOT_FOUND, "COMPETITOR_ACCOUNT_NOT_FOUND");
        }
        CompetitorAccount account = accountMapper.selectByIdAndWorkspace(accountId, workspaceId);
        if (account == null) {
            throw new BusinessException(CODE_COMPETITOR_ACCOUNT_NOT_FOUND, "COMPETITOR_ACCOUNT_NOT_FOUND");
        }

        // 证据必填（双重保险：服务层 + 数据库 CHECK）
        String evidenceText = request.getEvidenceText() == null ? null : request.getEvidenceText().trim();
        if (evidenceText == null || evidenceText.isEmpty()) {
            throw new BusinessException(CODE_COMPETITOR_INVALID,
                    "EVIDENCE_TEXT_REQUIRED: evidence_text_is_required");
        }

        String sourceType = upper(request.getSourceType());
        if (sourceType == null) {
            sourceType = CompetitorNote.SOURCE_MANUAL_IMPORT;
        }
        if (!CompetitorNote.ALLOWED_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(CODE_COMPETITOR_INVALID,
                    "COMPETITOR_INVALID: source_type_not_allowed");
        }

        OffsetDateTime now = OffsetDateTime.now();
        CompetitorNote note = new CompetitorNote();
        note.setId(UUID.randomUUID());
        note.setCompetitorAccountId(accountId);
        note.setWorkspaceId(workspaceId);
        note.setPlatformCode(account.getPlatformCode());
        note.setNoteTitle(blankToNull(request.getNoteTitle()));
        note.setNoteUrl(blankToNull(request.getNoteUrl()));
        note.setPublishedAt(request.getPublishedAt());
        note.setContentStructure(request.getContentStructure());
        // 互动数据统一口径，缺失补 0（不省略）
        note.setEngagement(normalizeEngagement(request.getEngagement()));
        note.setCapturedAt(now);
        note.setEvidenceText(evidenceText);
        note.setSourceType(sourceType);
        note.setCreatedAt(now);
        noteMapper.insert(note);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("noteId", note.getId().toString());
        meta.put("accountId", accountId.toString());
        meta.put("sourceType", sourceType);
        meta.put("evidenceLength", evidenceText.length());
        auditWriter.record("competitor.note.create", "COMPETITOR_NOTE",
                note.getId().toString(), meta);

        log.info("Competitor note created: id={} ws={} account={} source={}",
                note.getId(), workspaceId, accountId, sourceType);
        return CompetitorNoteCreateResponse.builder()
                .id(note.getId().toString())
                .capturedAt(note.getCapturedAt())
                .sourceType(note.getSourceType())
                .competitorAccountId(accountId.toString())
                .build();
    }

    /**
     * 分页列出某竞品账号下的笔记。
     */
    public PaginatedResponse<CompetitorNoteListItem> listNotes(String workspaceId, UUID accountId,
                                                               int page, int size) {
        requireWorkspace(workspaceId);
        if (accountId == null) {
            throw new BusinessException(CODE_COMPETITOR_ACCOUNT_NOT_FOUND, "COMPETITOR_ACCOUNT_NOT_FOUND");
        }
        if (accountMapper.selectByIdAndWorkspace(accountId, workspaceId) == null) {
            throw new BusinessException(CODE_COMPETITOR_ACCOUNT_NOT_FOUND, "COMPETITOR_ACCOUNT_NOT_FOUND");
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (long) (safePage - 1) * safeSize;

        List<CompetitorNoteListItem> items = noteMapper.listByAccount(accountId, workspaceId, offset, safeSize)
                .stream().map(this::toNoteListItem).toList();
        long total = noteMapper.countByAccount(accountId, workspaceId);
        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    /**
     * 查询单条竞品笔记。
     */
    public CompetitorNoteListItem getNote(String workspaceId, UUID accountId, UUID noteId) {
        requireWorkspace(workspaceId);
        CompetitorNote note = noteMapper.selectByIdAndWorkspace(noteId, workspaceId);
        if (note == null || !note.getCompetitorAccountId().equals(accountId)) {
            throw new BusinessException(CODE_COMPETITOR_ACCOUNT_NOT_FOUND, "COMPETITOR_NOTE_NOT_FOUND");
        }
        return toNoteListItem(note);
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部工具
    // ══════════════════════════════════════════════════════════════════

    /**
     * 互动数据统一口径归一：固定 5 键，缺失补 0。
     */
    private Map<String, Object> normalizeEngagement(Map<String, Object> input) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        Map<String, Object> source = input == null ? Map.of() : input;
        for (String key : CompetitorNote.ENGAGEMENT_KEYS) {
            Object value = source.get(key);
            normalized.put(key, value == null ? 0L : toLong(value));
        }
        return normalized;
    }

    private CompetitorNoteListItem toNoteListItem(CompetitorNote note) {
        return CompetitorNoteListItem.builder()
                .id(note.getId().toString())
                .noteTitle(note.getNoteTitle())
                .noteUrl(note.getNoteUrl())
                .publishedAt(note.getPublishedAt())
                .contentStructure(note.getContentStructure())
                .engagement(note.getEngagement())
                .capturedAt(note.getCapturedAt())
                .sourceType(note.getSourceType())
                .evidenceText(note.getEvidenceText())
                .build();
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_COMPETITOR_INVALID,
                    "COMPETITOR_INVALID: workspace_id_required");
        }
    }

    private String upper(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
