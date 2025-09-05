package com.emailMarketing.analytics;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="campaign_variant_stats")
public class CampaignVariantStats {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long campaignId; private String variantCode; private Integer sentCount; private Integer openCount; private Integer clickCount; private Integer bounceCount; private Double revenueAmount; private Integer ordersCount; private LocalDateTime lastCalculatedAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public String getVariantCode(){return variantCode;} public void setVariantCode(String variantCode){this.variantCode=variantCode;}
    public Integer getSentCount(){return sentCount;} public void setSentCount(Integer sentCount){this.sentCount=sentCount;}
    public Integer getOpenCount(){return openCount;} public void setOpenCount(Integer openCount){this.openCount=openCount;}
    public Integer getClickCount(){return clickCount;} public void setClickCount(Integer clickCount){this.clickCount=clickCount;}
    public Integer getBounceCount(){return bounceCount;} public void setBounceCount(Integer bounceCount){this.bounceCount=bounceCount;}
    public Double getRevenueAmount(){return revenueAmount;} public void setRevenueAmount(Double revenueAmount){this.revenueAmount=revenueAmount;}
    public Integer getOrdersCount(){return ordersCount;} public void setOrdersCount(Integer ordersCount){this.ordersCount=ordersCount;}
    public LocalDateTime getLastCalculatedAt(){return lastCalculatedAt;} public void setLastCalculatedAt(LocalDateTime lastCalculatedAt){this.lastCalculatedAt=lastCalculatedAt;}
}
