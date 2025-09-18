package com.emailMarketing.deliverability;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "unsubscribe_events", indexes = {
    @Index(name="idx_unsub_user_campaign", columnList="user_id,campaign_id"),
    @Index(name="idx_unsub_user_email", columnList="user_id,email")
})
public class UnsubscribeEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name="user_id", nullable=false) private Long userId;
  @Column(name="campaign_id") private Long campaignId;
  @Column(nullable=false, length=320) private String email;
  @Column(length=120) private String reason;
  @Column(name="created_at", nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
  public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
  public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
  public String getReason(){return reason;} public void setReason(String reason){this.reason=reason;}
  public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
