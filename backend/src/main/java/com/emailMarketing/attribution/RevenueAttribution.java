package com.emailMarketing.attribution;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="revenue_attribution")
public class RevenueAttribution {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private Long orderId; private Long campaignId; private String modelCode; private Long interactionId; private String channel; private Double weight; private Double attributedAmount; private String currency; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public Long getOrderId(){return orderId;} public void setOrderId(Long orderId){this.orderId=orderId;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public String getModelCode(){return modelCode;} public void setModelCode(String modelCode){this.modelCode=modelCode;}
    public Long getInteractionId(){return interactionId;} public void setInteractionId(Long interactionId){this.interactionId=interactionId;}
    public String getChannel(){return channel;} public void setChannel(String channel){this.channel=channel;}
    public Double getWeight(){return weight;} public void setWeight(Double weight){this.weight=weight;}
    public Double getAttributedAmount(){return attributedAmount;} public void setAttributedAmount(Double attributedAmount){this.attributedAmount=attributedAmount;}
    public String getCurrency(){return currency;} public void setCurrency(String currency){this.currency=currency;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
