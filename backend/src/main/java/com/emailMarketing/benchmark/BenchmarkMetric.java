package com.emailMarketing.benchmark;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="benchmark_metrics")
public class BenchmarkMetric {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String industry; private String listTier; private String region;
    private Double openRateMedian; private Double openRateTopQ; private Double openRateBottomQ;
    private Double clickRateMedian; private Double clickRateTopQ; private Double clickRateBottomQ;
    private Double conversionRateMedian; private Double conversionRateTopQ; private Double conversionRateBottomQ;
    private Double revenuePerEmailMedian; private Double revenuePerEmailTopQ; private Double revenuePerEmailBottomQ;
    private Double bounceRateMedian; private Double bounceRateTopQ; private Double bounceRateBottomQ;
    private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public String getIndustry(){return industry;} public void setIndustry(String industry){this.industry=industry;}
    public String getListTier(){return listTier;} public void setListTier(String listTier){this.listTier=listTier;}
    public String getRegion(){return region;} public void setRegion(String region){this.region=region;}
    public Double getOpenRateMedian(){return openRateMedian;} public void setOpenRateMedian(Double openRateMedian){this.openRateMedian=openRateMedian;}
    public Double getOpenRateTopQ(){return openRateTopQ;} public void setOpenRateTopQ(Double openRateTopQ){this.openRateTopQ=openRateTopQ;}
    public Double getOpenRateBottomQ(){return openRateBottomQ;} public void setOpenRateBottomQ(Double openRateBottomQ){this.openRateBottomQ=openRateBottomQ;}
    public Double getClickRateMedian(){return clickRateMedian;} public void setClickRateMedian(Double clickRateMedian){this.clickRateMedian=clickRateMedian;}
    public Double getClickRateTopQ(){return clickRateTopQ;} public void setClickRateTopQ(Double clickRateTopQ){this.clickRateTopQ=clickRateTopQ;}
    public Double getClickRateBottomQ(){return clickRateBottomQ;} public void setClickRateBottomQ(Double clickRateBottomQ){this.clickRateBottomQ=clickRateBottomQ;}
    public Double getConversionRateMedian(){return conversionRateMedian;} public void setConversionRateMedian(Double conversionRateMedian){this.conversionRateMedian=conversionRateMedian;}
    public Double getConversionRateTopQ(){return conversionRateTopQ;} public void setConversionRateTopQ(Double conversionRateTopQ){this.conversionRateTopQ=conversionRateTopQ;}
    public Double getConversionRateBottomQ(){return conversionRateBottomQ;} public void setConversionRateBottomQ(Double conversionRateBottomQ){this.conversionRateBottomQ=conversionRateBottomQ;}
    public Double getRevenuePerEmailMedian(){return revenuePerEmailMedian;} public void setRevenuePerEmailMedian(Double revenuePerEmailMedian){this.revenuePerEmailMedian=revenuePerEmailMedian;}
    public Double getRevenuePerEmailTopQ(){return revenuePerEmailTopQ;} public void setRevenuePerEmailTopQ(Double revenuePerEmailTopQ){this.revenuePerEmailTopQ=revenuePerEmailTopQ;}
    public Double getRevenuePerEmailBottomQ(){return revenuePerEmailBottomQ;} public void setRevenuePerEmailBottomQ(Double revenuePerEmailBottomQ){this.revenuePerEmailBottomQ=revenuePerEmailBottomQ;}
    public Double getBounceRateMedian(){return bounceRateMedian;} public void setBounceRateMedian(Double bounceRateMedian){this.bounceRateMedian=bounceRateMedian;}
    public Double getBounceRateTopQ(){return bounceRateTopQ;} public void setBounceRateTopQ(Double bounceRateTopQ){this.bounceRateTopQ=bounceRateTopQ;}
    public Double getBounceRateBottomQ(){return bounceRateBottomQ;} public void setBounceRateBottomQ(Double bounceRateBottomQ){this.bounceRateBottomQ=bounceRateBottomQ;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
