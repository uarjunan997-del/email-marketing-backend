package com.emailMarketing.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="campaign_roi")
public class CampaignRoi {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long campaignId; private Double revenueAmount; private String currency="USD"; private Integer attributedOrders; private LocalDateTime lastCalculatedAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public Double getRevenueAmount(){return revenueAmount;} public void setRevenueAmount(Double revenueAmount){this.revenueAmount=revenueAmount;}
    public String getCurrency(){return currency;} public void setCurrency(String currency){this.currency=currency;}
    public Integer getAttributedOrders(){return attributedOrders;} public void setAttributedOrders(Integer attributedOrders){this.attributedOrders=attributedOrders;}
    public LocalDateTime getLastCalculatedAt(){return lastCalculatedAt;} public void setLastCalculatedAt(LocalDateTime lastCalculatedAt){this.lastCalculatedAt=lastCalculatedAt;}
}
