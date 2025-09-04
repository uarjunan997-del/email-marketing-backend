package com.emailMarketing.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="campaigns", indexes = {@Index(name="idx_campaign_user", columnList="user_id")})
public class Campaign {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private String name;
    private String segment; // targeting segment
    private Long templateId;
    private String status = "DRAFT"; // DRAFT,SCHEDULED,SENDING,COMPLETED,FAILED
    private LocalDateTime scheduledAt; private LocalDateTime createdAt = LocalDateTime.now();
    private int totalRecipients; private int sentCount; private int openCount; private int clickCount;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public String getSegment(){return segment;} public void setSegment(String segment){this.segment=segment;}
    public Long getTemplateId(){return templateId;} public void setTemplateId(Long templateId){this.templateId=templateId;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public LocalDateTime getScheduledAt(){return scheduledAt;} public void setScheduledAt(LocalDateTime scheduledAt){this.scheduledAt=scheduledAt;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public int getTotalRecipients(){return totalRecipients;} public void setTotalRecipients(int totalRecipients){this.totalRecipients=totalRecipients;}
    public int getSentCount(){return sentCount;} public void setSentCount(int sentCount){this.sentCount=sentCount;}
    public int getOpenCount(){return openCount;} public void setOpenCount(int openCount){this.openCount=openCount;}
    public int getClickCount(){return clickCount;} public void setClickCount(int clickCount){this.clickCount=clickCount;}
}
