package com.emailMarketing.timeseries;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="email_metrics_daily_forecast")
public class EmailMetricDailyForecast {
    @EmbeddedId private Pk id; private double openRateForecast; private String model; private LocalDateTime createdAt;
    @PrePersist void pre(){ if(createdAt==null) createdAt = LocalDateTime.now(); if(model==null) model="HW_ADD"; }
    public Pk getId(){return id;} public void setId(Pk p){this.id=p;} public double getOpenRateForecast(){return openRateForecast;} public void setOpenRateForecast(double v){this.openRateForecast=v;} public String getModel(){return model;} public void setModel(String m){this.model=m;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime c){this.createdAt=c;}
    @Embeddable public static class Pk implements java.io.Serializable{ private Long userId; private LocalDateTime bucketStart; @Column(name="model", insertable=false, updatable=false) private String model;
        public Pk(){} public Pk(Long u, LocalDateTime b, String m){this.userId=u; this.bucketStart=b; this.model=m;}
        public Long getUserId(){return userId;} public void setUserId(Long u){this.userId=u;} public LocalDateTime getBucketStart(){return bucketStart;} public void setBucketStart(LocalDateTime b){this.bucketStart=b;} public String getModel(){return model;} public void setModel(String m){this.model=m;}
        @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof Pk other)) return false; return java.util.Objects.equals(userId, other.userId) && java.util.Objects.equals(bucketStart, other.bucketStart) && java.util.Objects.equals(model, other.model);} 
        @Override public int hashCode(){ return java.util.Objects.hash(userId,bucketStart,model);} }
}