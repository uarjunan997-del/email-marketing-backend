package com.emailMarketing.benchmark;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="benchmark_comparisons")
public class BenchmarkComparison {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private String industry; private String listTier; private String region; private LocalDateTime computedAt;
    private Double openRateUser; private Double clickRateUser; private Double conversionRateUser; private Double revenuePerEmailUser; private Double bounceRateUser;
    private Double openRateMedian; private Double clickRateMedian; private Double conversionRateMedian; private Double revenuePerEmailMedian; private Double bounceRateMedian;
    private Double openRatePercentile; private Double clickRatePercentile; private Double conversionRatePercentile; private Double bounceRatePercentile; private Double revenuePerEmailPercentile;
    private String performanceScore; @Lob private String recommendations;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getIndustry(){return industry;} public void setIndustry(String industry){this.industry=industry;}
    public String getListTier(){return listTier;} public void setListTier(String listTier){this.listTier=listTier;}
    public String getRegion(){return region;} public void setRegion(String region){this.region=region;}
    public LocalDateTime getComputedAt(){return computedAt;} public void setComputedAt(LocalDateTime computedAt){this.computedAt=computedAt;}
    public Double getOpenRateUser(){return openRateUser;} public void setOpenRateUser(Double openRateUser){this.openRateUser=openRateUser;}
    public Double getClickRateUser(){return clickRateUser;} public void setClickRateUser(Double clickRateUser){this.clickRateUser=clickRateUser;}
    public Double getConversionRateUser(){return conversionRateUser;} public void setConversionRateUser(Double conversionRateUser){this.conversionRateUser=conversionRateUser;}
    public Double getRevenuePerEmailUser(){return revenuePerEmailUser;} public void setRevenuePerEmailUser(Double revenuePerEmailUser){this.revenuePerEmailUser=revenuePerEmailUser;}
    public Double getBounceRateUser(){return bounceRateUser;} public void setBounceRateUser(Double bounceRateUser){this.bounceRateUser=bounceRateUser;}
    public Double getOpenRateMedian(){return openRateMedian;} public void setOpenRateMedian(Double openRateMedian){this.openRateMedian=openRateMedian;}
    public Double getClickRateMedian(){return clickRateMedian;} public void setClickRateMedian(Double clickRateMedian){this.clickRateMedian=clickRateMedian;}
    public Double getConversionRateMedian(){return conversionRateMedian;} public void setConversionRateMedian(Double conversionRateMedian){this.conversionRateMedian=conversionRateMedian;}
    public Double getRevenuePerEmailMedian(){return revenuePerEmailMedian;} public void setRevenuePerEmailMedian(Double revenuePerEmailMedian){this.revenuePerEmailMedian=revenuePerEmailMedian;}
    public Double getBounceRateMedian(){return bounceRateMedian;} public void setBounceRateMedian(Double bounceRateMedian){this.bounceRateMedian=bounceRateMedian;}
    public Double getOpenRatePercentile(){return openRatePercentile;} public void setOpenRatePercentile(Double openRatePercentile){this.openRatePercentile=openRatePercentile;}
    public Double getClickRatePercentile(){return clickRatePercentile;} public void setClickRatePercentile(Double clickRatePercentile){this.clickRatePercentile=clickRatePercentile;}
    public Double getConversionRatePercentile(){return conversionRatePercentile;} public void setConversionRatePercentile(Double conversionRatePercentile){this.conversionRatePercentile=conversionRatePercentile;}
    public Double getBounceRatePercentile(){return bounceRatePercentile;} public void setBounceRatePercentile(Double bounceRatePercentile){this.bounceRatePercentile=bounceRatePercentile;}
    public Double getRevenuePerEmailPercentile(){return revenuePerEmailPercentile;} public void setRevenuePerEmailPercentile(Double revenuePerEmailPercentile){this.revenuePerEmailPercentile=revenuePerEmailPercentile;}
    public String getPerformanceScore(){return performanceScore;} public void setPerformanceScore(String performanceScore){this.performanceScore=performanceScore;}
    public String getRecommendations(){return recommendations;} public void setRecommendations(String recommendations){this.recommendations=recommendations;}
}
