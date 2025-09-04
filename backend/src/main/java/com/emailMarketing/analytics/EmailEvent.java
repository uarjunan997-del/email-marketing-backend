package com.emailMarketing.analytics;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="email_events", indexes = {
  @Index(name="idx_ev_campaign", columnList="campaign_id"),
  @Index(name="idx_ev_user", columnList="user_id"),
  @Index(name="idx_ev_type", columnList="event_type")
})
public class EmailEvent {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id", nullable=false) private Long userId;
  @Column(name="campaign_id", nullable=false) private Long campaignId;
  @Column(name="event_type", nullable=false, length=32) private String eventType; // SENT, OPEN, CLICK, BOUNCE, SPAM
  private LocalDateTime eventTime = LocalDateTime.now();
  private String meta;
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
  public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
  public String getEventType(){return eventType;} public void setEventType(String eventType){this.eventType=eventType;}
  public LocalDateTime getEventTime(){return eventTime;} public void setEventTime(LocalDateTime eventTime){this.eventTime=eventTime;}
  public String getMeta(){return meta;} public void setMeta(String meta){this.meta=meta;}
}
