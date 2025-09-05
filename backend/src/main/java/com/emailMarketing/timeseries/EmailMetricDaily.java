package com.emailMarketing.timeseries;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="email_metrics_daily")
public class EmailMetricDaily {
    @EmbeddedId private Key id = new Key();
    private Long campaignId; private Integer sentCount; private Integer openCount; private Integer clickCount; private Integer bounceCount; private Integer orderCount; private Double revenueAmount;
    public Key getId(){return id;} public void setId(Key id){this.id=id;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public Integer getSentCount(){return sentCount;} public void setSentCount(Integer sentCount){this.sentCount=sentCount;}
    public Integer getOpenCount(){return openCount;} public void setOpenCount(Integer openCount){this.openCount=openCount;}
    public Integer getClickCount(){return clickCount;} public void setClickCount(Integer clickCount){this.clickCount=clickCount;}
    public Integer getBounceCount(){return bounceCount;} public void setBounceCount(Integer bounceCount){this.bounceCount=bounceCount;}
    public Integer getOrderCount(){return orderCount;} public void setOrderCount(Integer orderCount){this.orderCount=orderCount;}
    public Double getRevenueAmount(){return revenueAmount;} public void setRevenueAmount(Double revenueAmount){this.revenueAmount=revenueAmount;}
    @Embeddable
    public static class Key implements java.io.Serializable {
        private Long userId; private LocalDateTime bucketStart;
        public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
        public LocalDateTime getBucketStart(){return bucketStart;} public void setBucketStart(LocalDateTime bucketStart){this.bucketStart=bucketStart;}
        @Override public int hashCode(){ return java.util.Objects.hash(userId,bucketStart); }
        @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof Key k)) return false; return java.util.Objects.equals(userId,k.userId) && java.util.Objects.equals(bucketStart,k.bucketStart);}    }
}
