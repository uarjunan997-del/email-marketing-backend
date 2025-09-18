package com.emailMarketing.template.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "template_assets", indexes = @Index(name = "idx_template_assets_template", columnList = "template_id"))
public class TemplateAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long templateId;
    private String fileName;
    private String contentType;
    private String storageKey;
    private Long sizeBytes;
    private LocalDateTime createdAt = LocalDateTime.now();

    // Cached read URL (e.g., OCI PAR) and its expiry, so we can reuse until it expires
    @Column(length = 2000)
    private String cachedReadUrl;
    private LocalDateTime cachedReadExpiresAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCachedReadUrl() {
        return cachedReadUrl;
    }

    public void setCachedReadUrl(String cachedReadUrl) {
        this.cachedReadUrl = cachedReadUrl;
    }

    public LocalDateTime getCachedReadExpiresAt() {
        return cachedReadExpiresAt;
    }

    public void setCachedReadExpiresAt(LocalDateTime cachedReadExpiresAt) {
        this.cachedReadExpiresAt = cachedReadExpiresAt;
    }
}
