package com.shortvideoscripagent.xhsagentyunying.freeze;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 阶段 0：平台表实体。
 */
@TableName(value = "app.platform", autoResultMap = true)
public class PlatformEntity {

    @TableId(value = "code", type = IdType.INPUT)
    private String code;

    @TableField("display_name")
    private String displayName;

    @TableField("connector_level")
    private String connectorLevel;

    @TableField("rollout_status")
    private String rolloutStatus;

    @TableField("write_enabled")
    private Boolean writeEnabled;

    @TableField("read_enabled")
    private Boolean readEnabled;

    @TableField("risk_note")
    private String riskNote;

    @TableField("legal_reviewed_at")
    private OffsetDateTime legalReviewedAt;

    @TableField("legal_reviewer")
    private String legalReviewer;

    @TableField("notes")
    private String notes;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getConnectorLevel() { return connectorLevel; }
    public void setConnectorLevel(String connectorLevel) { this.connectorLevel = connectorLevel; }
    public String getRolloutStatus() { return rolloutStatus; }
    public void setRolloutStatus(String rolloutStatus) { this.rolloutStatus = rolloutStatus; }
    public Boolean getWriteEnabled() { return writeEnabled; }
    public void setWriteEnabled(Boolean writeEnabled) { this.writeEnabled = writeEnabled; }
    public Boolean getReadEnabled() { return readEnabled; }
    public void setReadEnabled(Boolean readEnabled) { this.readEnabled = readEnabled; }
    public String getRiskNote() { return riskNote; }
    public void setRiskNote(String riskNote) { this.riskNote = riskNote; }
    public OffsetDateTime getLegalReviewedAt() { return legalReviewedAt; }
    public void setLegalReviewedAt(OffsetDateTime legalReviewedAt) { this.legalReviewedAt = legalReviewedAt; }
    public String getLegalReviewer() { return legalReviewer; }
    public void setLegalReviewer(String legalReviewer) { this.legalReviewer = legalReviewer; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
