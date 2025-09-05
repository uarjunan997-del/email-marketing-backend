package com.emailMarketing.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="campaign_approvals")
public class CampaignApproval {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long campaignId; private Long approverUserId; private String status = "PENDING"; private LocalDateTime decisionAt; private String notes; private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public Long getApproverUserId(){return approverUserId;} public void setApproverUserId(Long approverUserId){this.approverUserId=approverUserId;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public LocalDateTime getDecisionAt(){return decisionAt;} public void setDecisionAt(LocalDateTime decisionAt){this.decisionAt=decisionAt;}
    public String getNotes(){return notes;} public void setNotes(String notes){this.notes=notes;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
