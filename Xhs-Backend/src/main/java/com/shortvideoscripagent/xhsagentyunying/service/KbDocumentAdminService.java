package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbSourceDescriptor;
import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbSourceLoader;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.KbDocumentMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentChunkSummary;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentDeleteResult;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentDetail;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentIndexStatus;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentSearchHit;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentSummary;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S3-11 知识库管理面 Service。
 * <p>
 * 提供文档列表 / 详情 / 关键词检索 / 删除 / 逐文档索引状态五类能力。
 * </p>
 * <p>
 * <b>workspace 隔离取舍</b>：知识库文档是<b>全局共享</b>的
 * （{@code kb.kb_document.workspace_id} 可空，源目录 {@code app.kb.source-dir} 也是全局的），
 * 因此列表 / 检索<b>刻意不按 workspace 过滤</b>；但管理动作（尤其删除）仍限制为
 * ADMIN / OWNER，且写审计留痕。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocumentAdminService {

    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_NOT_FOUND = 40460;
    private static final int CODE_BAD_REQUEST = 40061;

    /** 命中片段最大长度。 */
    private static final int SNIPPET_MAX_LENGTH = 200;

    /** 命中片段上下文长度（命中点前后各取多少字符）。 */
    private static final int SNIPPET_CONTEXT = 60;

    private final KbDocumentMapper kbDocumentMapper;
    private final KbSourceLoader sourceLoader;
    private final WorkspaceService workspaceService;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    // ── 权限 ──────────────────────────────────────────────────────────────

    /**
     * 要求当前用户对当前 workspace 具有 ADMIN 及以上权限（OWNER 天然满足）。
     */
    public void requireKbAdmin() {
        Long userId = RequestContext.getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "unauthorized");
        }
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
        if (!role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "kb_admin_role_required");
        }
    }

    // ── 列表 ──────────────────────────────────────────────────────────────

    /**
     * 分页列出知识库文档（按 doc_id 聚合后分页，总数与聚合结果一致）。
     *
     * @param docType     文档类型，空串表示不过滤
     * @param contentType 内容类型，空串表示不过滤
     * @param keyword     doc_id / title 关键字，空串表示不过滤
     */
    public PaginatedResponse<KbDocumentSummary> list(
            String docType,
            String contentType,
            String keyword,
            int page,
            int size
    ) {
        requireKbAdmin();

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (safePage - 1L) * safeSize;

        String safeDocType = normalize(docType);
        String safeContentType = normalize(contentType);
        String safeKeyword = normalize(keyword);

        List<KbDocumentMapper.KbDocumentRow> rows = kbDocumentMapper.selectAggregatedDocuments(
                safeDocType, safeContentType, safeKeyword, offset, safeSize);
        long total = kbDocumentMapper.countAggregatedDocuments(safeDocType, safeContentType, safeKeyword);

        List<KbDocumentSummary> items = rows.stream().map(this::toSummary).toList();
        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    // ── 详情 ──────────────────────────────────────────────────────────────

    /**
     * 查询单个文档的 chunk 明细（不返回 embedding，也不返回完整正文）。
     */
    public KbDocumentDetail getDocument(String docId) {
        requireKbAdmin();
        String safeDocId = requireDocId(docId);

        KbDocumentMapper.KbDocumentRow row = kbDocumentMapper.selectAggregatedDocumentByDocId(safeDocId);
        if (row == null) {
            throw new BusinessException(CODE_NOT_FOUND, "kb_document_not_found");
        }

        List<KbDocumentChunkSummary> chunks = kbDocumentMapper.selectChunkSummaries(safeDocId).stream()
                .map(c -> {
                    KbDocumentChunkSummary summary = new KbDocumentChunkSummary();
                    summary.setChunkType(c.getChunkType());
                    summary.setContentLength(c.getContentLength() == null ? 0 : c.getContentLength());
                    summary.setHasEmbedding(Boolean.TRUE.equals(c.getHasEmbedding()));
                    summary.setUpdatedAt(c.getUpdatedAt());
                    return summary;
                })
                .toList();

        KbDocumentDetail detail = new KbDocumentDetail();
        detail.setDocId(row.getDocId());
        detail.setTitle(row.getTitle());
        detail.setDocType(row.getDocType());
        detail.setContentType(row.getContentType());
        detail.setPersona(parseStringList(row.getPersonaJson()));
        detail.setTags(parseStringList(row.getTagsJson()));
        detail.setChunkCount(row.getChunkCount());
        detail.setHasEmbedding(Boolean.TRUE.equals(row.getHasEmbedding()));
        detail.setCreatedAt(row.getCreatedAt());
        detail.setUpdatedAt(row.getUpdatedAt());
        detail.setWorkspaceId(row.getWorkspaceId());
        detail.setChunks(chunks);
        return detail;
    }

    // ── 检索 ──────────────────────────────────────────────────────────────

    /**
     * 关键词检索（在 title / content 上 ILIKE）。
     *
     * @param docTypes 限定文档类型；空表示不限
     */
    public List<KbDocumentSearchHit> search(String keyword, List<String> docTypes, int limit) {
        requireKbAdmin();

        String safeKeyword = normalize(keyword);
        if (safeKeyword.isEmpty()) {
            throw new BusinessException(CODE_BAD_REQUEST, "kb_keyword_required");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<String> safeDocTypes = docTypes == null
                ? List.of()
                : docTypes.stream().map(KbDocumentAdminService::normalize).filter(s -> !s.isEmpty()).distinct().toList();

        List<KbDocumentMapper.KbSearchRow> rows =
                kbDocumentMapper.searchByKeyword(safeKeyword, safeDocTypes, safeLimit * 4L);

        // 按 doc_id 去重，保留首个命中片段
        Map<String, KbDocumentSearchHit> deduped = new LinkedHashMap<>();
        for (KbDocumentMapper.KbSearchRow row : rows) {
            if (deduped.containsKey(row.getDocId())) {
                continue;
            }
            KbDocumentSearchHit hit = new KbDocumentSearchHit();
            hit.setDocId(row.getDocId());
            hit.setTitle(row.getTitle());
            hit.setDocType(row.getDocType());
            hit.setContentType(row.getContentType());
            hit.setMatchedField(matchedField(row.getTitle(), row.getContent(), safeKeyword));
            hit.setSnippet(buildSnippet(row.getContent(), safeKeyword));
            deduped.put(row.getDocId(), hit);
            if (deduped.size() >= safeLimit) {
                break;
            }
        }
        return new ArrayList<>(deduped.values());
    }

    // ── 删除 ──────────────────────────────────────────────────────────────

    /**
     * 删除某个文档的全部 chunk，并尝试清理源文件。
     * <p>
     * 源文件只在<b>唯一匹配</b>时才删除；匹配不到或匹配到多个时不删文件，
     * 仅在结果中给出提示，避免误删他人文档。
     * </p>
     */
    @Transactional
    public KbDocumentDeleteResult deleteDocument(String docId) {
        requireKbAdmin();
        String safeDocId = requireDocId(docId);

        long chunkCount = kbDocumentMapper.countChunksByDocId(safeDocId);
        if (chunkCount == 0) {
            throw new BusinessException(CODE_NOT_FOUND, "kb_document_not_found");
        }

        int deleted = kbDocumentMapper.deleteByDocId(safeDocId);

        boolean sourceRemoved = false;
        String note;
        try {
            List<KbSourceDescriptor> matches = findSourceMatches(safeDocId);
            if (matches.isEmpty()) {
                note = "源文件未清理：未在源目录找到与该 docId 唯一匹配的源文件";
            } else if (matches.size() > 1) {
                note = "源文件未清理：匹配到多个源文件，为避免误删已跳过（"
                        + matches.size() + " 个候选）";
            } else {
                Path path = matches.get(0).sourcePath();
                try {
                    Files.deleteIfExists(path);
                    sourceRemoved = true;
                    note = "源文件已删除：" + path;
                } catch (IOException ex) {
                    note = "源文件未清理：删除失败（" + ex.getMessage() + "）";
                    log.warn("Failed to delete KB source file {}: {}", path, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            note = "源文件未清理：读取源目录失败（" + ex.getMessage() + "）";
            log.warn("Failed to scan KB source dir for docId {}: {}", safeDocId, ex.getMessage());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("deletedChunks", deleted);
        metadata.put("sourceFileRemoved", sourceRemoved);
        metadata.put("cleanupNote", note);
        auditWriter.record("kb.document.delete", "KB_DOCUMENT", safeDocId, metadata);

        KbDocumentDeleteResult result = new KbDocumentDeleteResult();
        result.setDocId(safeDocId);
        result.setDeletedChunks(deleted);
        result.setSourceFileRemoved(sourceRemoved);
        result.setSourceFileCleanupNote(note);
        return result;
    }

    // ── 索引状态 ──────────────────────────────────────────────────────────

    /**
     * 查询单个文档的索引状态。
     * <p>
     * {@code stale} 判定：源文件的 {@code lastModified} 晚于该 doc 所有 chunk 的
     * {@code max(updated_at)}。
     * </p>
     */
    public KbDocumentIndexStatus indexStatus(String docId) {
        requireKbAdmin();
        String safeDocId = requireDocId(docId);

        long chunkCount = kbDocumentMapper.countChunksByDocId(safeDocId);
        if (chunkCount == 0) {
            throw new BusinessException(CODE_NOT_FOUND, "kb_document_not_found");
        }
        long embeddedCount = kbDocumentMapper.countEmbeddedChunksByDocId(safeDocId);
        OffsetDateTime lastIndexedAt = kbDocumentMapper.selectMaxUpdatedAt(safeDocId);

        KbDocumentIndexStatus status = new KbDocumentIndexStatus();
        status.setDocId(safeDocId);
        status.setChunkCount(chunkCount);
        status.setEmbeddedChunkCount(embeddedCount);
        status.setLastIndexedAt(lastIndexedAt);

        try {
            List<KbSourceDescriptor> matches = findSourceMatches(safeDocId);
            if (!matches.isEmpty()) {
                Path path = matches.get(0).sourcePath();
                status.setSourceFilePresent(true);
                status.setSourceFilePath(path.toString());
                Instant fileModified = Files.getLastModifiedTime(path).toInstant();
                Instant indexedAt = lastIndexedAt == null ? Instant.EPOCH : lastIndexedAt.toInstant();
                status.setStale(fileModified.isAfter(indexedAt));
            } else {
                status.setSourceFilePresent(false);
                status.setSourceFilePath(null);
                status.setStale(false);
            }
        } catch (IOException ex) {
            log.warn("Failed to resolve KB source file for docId {}: {}", safeDocId, ex.getMessage());
            status.setSourceFilePresent(false);
            status.setSourceFilePath(null);
            status.setStale(false);
        }
        return status;
    }

    // ── private ───────────────────────────────────────────────────────────

    /**
     * 查找与 docId 唯一匹配的源文件描述符。
     * <p>
     * {@link KbSourceLoader} 已按 front-matter {@code doc_id} 或文件名（去扩展名）推导 docId，
     * 因此这里直接按 {@code descriptor.docId()} 匹配即可覆盖两种来源。
     * </p>
     */
    private List<KbSourceDescriptor> findSourceMatches(String docId) throws IOException {
        List<KbSourceDescriptor> matches = new ArrayList<>();
        for (KbSourceDescriptor descriptor : sourceLoader.loadAll()) {
            if (docId.equals(descriptor.docId())) {
                matches.add(descriptor);
            }
        }
        return matches;
    }

    private KbDocumentSummary toSummary(KbDocumentMapper.KbDocumentRow row) {
        KbDocumentSummary summary = new KbDocumentSummary();
        summary.setDocId(row.getDocId());
        summary.setTitle(row.getTitle());
        summary.setDocType(row.getDocType());
        summary.setContentType(row.getContentType());
        summary.setPersona(parseStringList(row.getPersonaJson()));
        summary.setTags(parseStringList(row.getTagsJson()));
        summary.setChunkCount(row.getChunkCount());
        summary.setHasEmbedding(Boolean.TRUE.equals(row.getHasEmbedding()));
        summary.setCreatedAt(row.getCreatedAt());
        summary.setUpdatedAt(row.getUpdatedAt());
        summary.setWorkspaceId(row.getWorkspaceId());
        return summary;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse array json: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String matchedField(String title, String content, String keyword) {
        if (title != null && title.toLowerCase().contains(keyword.toLowerCase())) {
            return "title";
        }
        if (content != null && content.toLowerCase().contains(keyword.toLowerCase())) {
            return "content";
        }
        return "content";
    }

    /**
     * 构造命中片段：以首次命中位置为中心，截断到 {@link #SNIPPET_MAX_LENGTH} 字符。
     */
    private static String buildSnippet(String content, String keyword) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        int idx = content.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return content.length() <= SNIPPET_MAX_LENGTH
                    ? content
                    : content.substring(0, SNIPPET_MAX_LENGTH) + "…";
        }
        int start = Math.max(0, idx - SNIPPET_CONTEXT);
        int end = Math.min(content.length(), idx + keyword.length() + SNIPPET_CONTEXT);
        StringBuilder sb = new StringBuilder();
        if (start > 0) {
            sb.append("…");
        }
        sb.append(content, start, end);
        if (end < content.length()) {
            sb.append("…");
        }
        String snippet = sb.toString();
        if (snippet.length() > SNIPPET_MAX_LENGTH) {
            snippet = snippet.substring(0, SNIPPET_MAX_LENGTH) + "…";
        }
        return snippet;
    }

    private static String requireDocId(String docId) {
        String safe = normalize(docId);
        if (safe.isEmpty()) {
            throw new BusinessException(CODE_BAD_REQUEST, "kb_doc_id_required");
        }
        return safe;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
