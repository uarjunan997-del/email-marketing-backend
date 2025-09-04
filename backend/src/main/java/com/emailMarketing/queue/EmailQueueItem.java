package com.emailMarketing.queue;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="email_queue", indexes = {@Index(name="idx_queue_status", columnList="status"), @Index(name="idx_queue_campaign", columnList="campaign_id")})
public class EmailQueueItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long campaignId; private Long userId; private String recipient; private String subject;
    @Lob private String body; private String status="PENDING"; private int attempt=0; private LocalDateTime nextAttemptAt = LocalDateTime.now();
    private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getRecipient(){return recipient;} public void setRecipient(String recipient){this.recipient=recipient;}
    public String getSubject(){return subject;} public void setSubject(String subject){this.subject=subject;}
    public String getBody(){return body;} public void setBody(String body){this.body=body;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public int getAttempt(){return attempt;} public void setAttempt(int attempt){this.attempt=attempt;}
    public LocalDateTime getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(LocalDateTime nextAttemptAt){this.nextAttemptAt=nextAttemptAt;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
