package com.emailMarketing.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="campaign_ab_tests")
public class CampaignABTest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long campaignId; private String variantCode; private String subjectLine; private Long templateId; private Integer sendSplitPercent; private int sentCount; private int openCount; private int clickCount; private Boolean winner; private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public String getVariantCode(){return variantCode;} public void setVariantCode(String variantCode){this.variantCode=variantCode;}
    public String getSubjectLine(){return subjectLine;} public void setSubjectLine(String subjectLine){this.subjectLine=subjectLine;}
    public Long getTemplateId(){return templateId;} public void setTemplateId(Long templateId){this.templateId=templateId;}
    public Integer getSendSplitPercent(){return sendSplitPercent;} public void setSendSplitPercent(Integer sendSplitPercent){this.sendSplitPercent=sendSplitPercent;}
    public int getSentCount(){return sentCount;} public void setSentCount(int sentCount){this.sentCount=sentCount;}
    public int getOpenCount(){return openCount;} public void setOpenCount(int openCount){this.openCount=openCount;}
    public int getClickCount(){return clickCount;} public void setClickCount(int clickCount){this.clickCount=clickCount;}
    public Boolean getWinner(){return winner;} public void setWinner(Boolean winner){this.winner=winner;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
