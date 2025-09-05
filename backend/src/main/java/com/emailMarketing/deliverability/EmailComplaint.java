package com.emailMarketing.deliverability;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="email_complaints", indexes = {@Index(name="idx_complaint_user_email", columnList="user_id,email")})
public class EmailComplaint {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    private Long campaignId; @Column(nullable=false) private String email;
    private String source; private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
    public String getSource(){return source;} public void setSource(String source){this.source=source;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
