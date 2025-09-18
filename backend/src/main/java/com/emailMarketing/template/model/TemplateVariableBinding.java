package com.emailMarketing.template.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "template_variable_bindings",
       uniqueConstraints = @UniqueConstraint(name = "uq_tvb_template_var", columnNames = {"template_id", "var_name"}),
       indexes = {
           @Index(name = "idx_tvb_template", columnList = "template_id"),
           @Index(name = "idx_tvb_source", columnList = "source_type, source_key")
       })
public class TemplateVariableBinding {

    public enum SourceType { CONTACT_COLUMN, CUSTOM_FIELD, STATIC, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "var_name", nullable = false, length = 200)
    private String varName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    @Column(name = "source_key", length = 255)
    private String sourceKey;

    @Column(name = "default_value", length = 4000)
    private String defaultValue;

    @Lob
    @Column(name = "transform_json")
    private String transformJson;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate(){
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate(){
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getVarName() { return varName; }
    public void setVarName(String varName) { this.varName = varName; }
    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getTransformJson() { return transformJson; }
    public void setTransformJson(String transformJson) { this.transformJson = transformJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
