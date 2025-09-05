package com.emailMarketing.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="sending_queue", indexes = {@Index(name="idx_sq_status_priority", columnList="status,priority"), @Index(name="idx_sq_campaign", columnList="campaignId")})
public class SendingQueueItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long campaignId; private Long userId; private String recipient; private String subject; @Lob private String body; private int priority=5; private String status="PENDING"; private int attempt; private int maxAttempts=3; private LocalDateTime nextAttemptAt=LocalDateTime.now(); private String lastError; private LocalDateTime createdAt=LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getRecipient(){return recipient;} public void setRecipient(String recipient){this.recipient=recipient;}
    public String getSubject(){return subject;} public void setSubject(String subject){this.subject=subject;}
    public String getBody(){return body;} public void setBody(String body){this.body=body;}
    public int getPriority(){return priority;} public void setPriority(int priority){this.priority=priority;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public int getAttempt(){return attempt;} public void setAttempt(int attempt){this.attempt=attempt;}
    public int getMaxAttempts(){return maxAttempts;} public void setMaxAttempts(int maxAttempts){this.maxAttempts=maxAttempts;}
    public LocalDateTime getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(LocalDateTime nextAttemptAt){this.nextAttemptAt=nextAttemptAt;}
    public String getLastError(){return lastError;} public void setLastError(String lastError){this.lastError=lastError;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
