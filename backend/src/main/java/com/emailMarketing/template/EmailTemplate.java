package com.emailMarketing.template;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_templates", indexes = @Index(name = "idx_template_user", columnList = "user_id"))
public class EmailTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String name;
    @Lob
    @Column(nullable = false)
    private String html;
    private Long categoryId;
    private String description;
    private String tags;
    private boolean isShared = false;
    private Long baseTemplateId;
    @Lob
    private String mjmlSource; // optional raw MJML before rendering
    @Lob
    private String lastRenderedHtml; // snapshot when mjml rendered
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Lob
    @Column(name = "ai_meta_json")
    private String aiMetaJson; // JSON blob storing AI generator meta (prompt,type,style,colors,model,...)

    @Lob
    @Column(name = "easy_email_design_json")
    private String easyEmailDesignJson; // Raw design JSON from easy-email-editor (for structured editing)

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public boolean isShared() {
        return isShared;
    }

    public void setShared(boolean shared) {
        isShared = shared;
    }

    public Long getBaseTemplateId() {
        return baseTemplateId;
    }

    public void setBaseTemplateId(Long baseTemplateId) {
        this.baseTemplateId = baseTemplateId;
    }

    public String getMjmlSource() {
        return mjmlSource;
    }

    public void setMjmlSource(String mjmlSource) {
        this.mjmlSource = mjmlSource;
    }

    public String getLastRenderedHtml() {
        return lastRenderedHtml;
    }

    public void setLastRenderedHtml(String lastRenderedHtml) {
        this.lastRenderedHtml = lastRenderedHtml;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAiMetaJson() {
        return aiMetaJson;
    }

    public void setAiMetaJson(String aiMetaJson) {
        this.aiMetaJson = aiMetaJson;
    }

    public String getEasyEmailDesignJson() {
        return easyEmailDesignJson;
    }

    public void setEasyEmailDesignJson(String easyEmailDesignJson) {
        this.easyEmailDesignJson = easyEmailDesignJson;
    }
}
