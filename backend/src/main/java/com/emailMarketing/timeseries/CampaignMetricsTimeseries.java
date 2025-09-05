package com.emailMarketing.timeseries;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="CAMPAIGN_METRICS_TIMESERIES")
public class CampaignMetricsTimeseries {
    @EmbeddedId private Key id = new Key();
    private Long sentCount; private Long deliveredCount; private Long openedCount; private Long clickedCount; private Long convertedCount; private Double revenue;
    public Key getId(){return id;} public void setId(Key k){this.id=k;}
    public Long getSentCount(){return sentCount;} public void setSentCount(Long v){this.sentCount=v;}
    public Long getDeliveredCount(){return deliveredCount;} public void setDeliveredCount(Long v){this.deliveredCount=v;}
    public Long getOpenedCount(){return openedCount;} public void setOpenedCount(Long v){this.openedCount=v;}
    public Long getClickedCount(){return clickedCount;} public void setClickedCount(Long v){this.clickedCount=v;}
    public Long getConvertedCount(){return convertedCount;} public void setConvertedCount(Long v){this.convertedCount=v;}
    public Double getRevenue(){return revenue;} public void setRevenue(Double v){this.revenue=v;}
    @Embeddable public static class Key implements java.io.Serializable { private Long campaignId; private LocalDate metricDate; private Integer metricHour; public Long getCampaignId(){return campaignId;} public void setCampaignId(Long c){this.campaignId=c;} public LocalDate getMetricDate(){return metricDate;} public void setMetricDate(LocalDate d){this.metricDate=d;} public Integer getMetricHour(){return metricHour;} public void setMetricHour(Integer h){this.metricHour=h;} @Override public int hashCode(){return java.util.Objects.hash(campaignId,metricDate,metricHour);} @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof Key k)) return false; return java.util.Objects.equals(campaignId,k.campaignId)&&java.util.Objects.equals(metricDate,k.metricDate)&&java.util.Objects.equals(metricHour,k.metricHour);} }
}
