package com.emailMarketing.analytics;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="campaign_order_attribution")
public class CampaignOrderAttribution {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private Long campaignId; private Long orderId; private String attributionType; private Double weight; private Double attributedAmount; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public Long getOrderId(){return orderId;} public void setOrderId(Long orderId){this.orderId=orderId;}
    public String getAttributionType(){return attributionType;} public void setAttributionType(String attributionType){this.attributionType=attributionType;}
    public Double getWeight(){return weight;} public void setWeight(Double weight){this.weight=weight;}
    public Double getAttributedAmount(){return attributedAmount;} public void setAttributedAmount(Double attributedAmount){this.attributedAmount=attributedAmount;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
