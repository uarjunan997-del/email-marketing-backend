package com.emailMarketing.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="campaign_schedules")
public class CampaignSchedule {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long campaignId;
    @Column(nullable=false) private String timezone;
    @Column(nullable=false) private LocalDateTime scheduledTime;
    private LocalDateTime sendWindowStart; private LocalDateTime sendWindowEnd;
    private String optimizationStrategy; // BEST_TIME,FIXED,BATCHED
    private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public String getTimezone(){return timezone;} public void setTimezone(String timezone){this.timezone=timezone;}
    public LocalDateTime getScheduledTime(){return scheduledTime;} public void setScheduledTime(LocalDateTime scheduledTime){this.scheduledTime=scheduledTime;}
    public LocalDateTime getSendWindowStart(){return sendWindowStart;} public void setSendWindowStart(LocalDateTime sendWindowStart){this.sendWindowStart=sendWindowStart;}
    public LocalDateTime getSendWindowEnd(){return sendWindowEnd;} public void setSendWindowEnd(LocalDateTime sendWindowEnd){this.sendWindowEnd=sendWindowEnd;}
    public String getOptimizationStrategy(){return optimizationStrategy;} public void setOptimizationStrategy(String optimizationStrategy){this.optimizationStrategy=optimizationStrategy;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
