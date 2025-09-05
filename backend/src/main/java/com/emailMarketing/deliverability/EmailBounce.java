package com.emailMarketing.deliverability;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="email_bounces", indexes = {@Index(name="idx_bounce_user_email", columnList="user_id,email")})
public class EmailBounce {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    private Long campaignId; @Column(nullable=false) private String email;
    private String bounceType; @Lob private String reason; private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
    public String getBounceType(){return bounceType;} public void setBounceType(String bounceType){this.bounceType=bounceType;}
    public String getReason(){return reason;} public void setReason(String reason){this.reason=reason;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
