package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.TextArrayTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 知识库文档切片（{@code kb.kb_document}）。
 * <p>
 * 一篇文档（{@code doc_id}）可能对应多个 chunk（{@code chunk_type}），唯一键为
 * {@code (doc_id, chunk_type)}。
 * </p>
 * <p>
 * 刻意不映射 {@code embedding}（{@code vector(1024)}）与 {@code tsv}（generated tsvector）：
 * <ul>
 *   <li>embedding 由 {@code KbDocumentStore} 裸 SQL 写入，管理面不需要读出 1024 维向量；</li>
 *   <li>{@code tsv} 是 generated 列，PostgreSQL 不允许对其做普通 UPDATE/INSERT。</li>
 * </ul>
 * </p>
 * <p>
 * 注意表名带 {@code kb.} schema 前缀——数据源 {@code currentSchema=app}，
 * 若写 {@code kb_document} 会落到 {@code app.kb_document}（不存在）。
 * </p>
 */
@Data
@TableName(value = "kb.kb_document", autoResultMap = true)
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档业务 ID（同一 doc 的多个 chunk 共享）。 */
    private String docId;

    /** 文档类型，如 viral_case / interview_experience。 */
    private String docType;

    /** 内容类型，如 experience / case。 */
    private String contentType;

    /** 适用人群标签（TEXT[]）。 */
    @TableField(typeHandler = TextArrayTypeHandler.class)
    private List<String> persona;

    /** 主题标签（TEXT[]）。 */
    @TableField(typeHandler = TextArrayTypeHandler.class)
    private List<String> tags;

    private String title;

    /** 切片类型，默认 'full'。 */
    private String chunkType;

    private String content;

    /** 元数据（JSONB，字符串形式）。 */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String metadata;

    /** 所属工作区（VARCHAR(32)，可空——知识库默认全局共享）。 */
    private String workspaceId;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
