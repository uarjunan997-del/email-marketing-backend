package com.emailMarketing.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_recipients", indexes = { @Index(name = "idx_crec_campaign", columnList = "campaignId"),
        @Index(name = "idx_crec_status", columnList = "status") })
public class CampaignRecipient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long campaignId;
    private Long contactId;
    @Column(nullable = false)
    private String email;
    private String status = "PENDING";
    private LocalDateTime lastAttemptAt;
    private int attempt;
    private String failureReason;
    private Integer engagementScore;
    private String variantCode;
    private LocalDateTime firstOpenAt;
    private LocalDateTime firstClickAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getEngagementScore() {
        return engagementScore;
    }

    public void setEngagementScore(Integer engagementScore) {
        this.engagementScore = engagementScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getVariantCode() {
        return variantCode;
    }

    public void setVariantCode(String variantCode) {
        this.variantCode = variantCode;
    }

    public LocalDateTime getFirstOpenAt() {
        return firstOpenAt;
    }

    public void setFirstOpenAt(LocalDateTime firstOpenAt) {
        this.firstOpenAt = firstOpenAt;
    }

    public LocalDateTime getFirstClickAt() {
        return firstClickAt;
    }

    public void setFirstClickAt(LocalDateTime firstClickAt) {
        this.firstClickAt = firstClickAt;
    }
}
