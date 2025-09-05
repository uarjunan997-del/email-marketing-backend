package com.emailMarketing.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="campaigns", indexes = {@Index(name="idx_campaign_user", columnList="user_id")})
public class Campaign {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private String name;
    private String segment; // targeting segment or segment code
    private Long templateId;
    private String subject; private String preheader;
    private String status = "DRAFT"; // DRAFT,REVIEW,SCHEDULED,SENDING,SENT,ANALYZED,FAILED,CANCELLED
    private String approvalStatus = "NOT_REQUIRED"; // NOT_REQUIRED,PENDING,APPROVED,REJECTED
    private LocalDateTime scheduledAt; private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime reviewRequestedAt; private LocalDateTime approvedAt; private LocalDateTime analyzedAt;
    @Lob private String metadataJson; // arbitrary JSON (targeting snapshot, model predictions, etc.)
    private int totalRecipients; private int sentCount; private int openCount; private int clickCount;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public String getSegment(){return segment;} public void setSegment(String segment){this.segment=segment;}
    public Long getTemplateId(){return templateId;} public void setTemplateId(Long templateId){this.templateId=templateId;}
    public String getSubject(){return subject;} public void setSubject(String subject){this.subject=subject;}
    public String getPreheader(){return preheader;} public void setPreheader(String preheader){this.preheader=preheader;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public String getApprovalStatus(){return approvalStatus;} public void setApprovalStatus(String approvalStatus){this.approvalStatus=approvalStatus;}
    public LocalDateTime getScheduledAt(){return scheduledAt;} public void setScheduledAt(LocalDateTime scheduledAt){this.scheduledAt=scheduledAt;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getReviewRequestedAt(){return reviewRequestedAt;} public void setReviewRequestedAt(LocalDateTime reviewRequestedAt){this.reviewRequestedAt=reviewRequestedAt;}
    public LocalDateTime getApprovedAt(){return approvedAt;} public void setApprovedAt(LocalDateTime approvedAt){this.approvedAt=approvedAt;}
    public LocalDateTime getAnalyzedAt(){return analyzedAt;} public void setAnalyzedAt(LocalDateTime analyzedAt){this.analyzedAt=analyzedAt;}
    public String getMetadataJson(){return metadataJson;} public void setMetadataJson(String metadataJson){this.metadataJson=metadataJson;}
    public int getTotalRecipients(){return totalRecipients;} public void setTotalRecipients(int totalRecipients){this.totalRecipients=totalRecipients;}
    public int getSentCount(){return sentCount;} public void setSentCount(int sentCount){this.sentCount=sentCount;}
    public int getOpenCount(){return openCount;} public void setOpenCount(int openCount){this.openCount=openCount;}
    public int getClickCount(){return clickCount;} public void setClickCount(int clickCount){this.clickCount=clickCount;}
}
