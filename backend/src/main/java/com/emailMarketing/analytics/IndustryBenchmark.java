package com.emailMarketing.analytics;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="industry_benchmarks")
public class IndustryBenchmark {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String vertical; private String listTier; private Double avgOpenRate; private Double avgClickRate; private Double avgCtr; private Double avgRoyPerEmail; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public String getVertical(){return vertical;} public void setVertical(String vertical){this.vertical=vertical;}
    public String getListTier(){return listTier;} public void setListTier(String listTier){this.listTier=listTier;}
    public Double getAvgOpenRate(){return avgOpenRate;} public void setAvgOpenRate(Double avgOpenRate){this.avgOpenRate=avgOpenRate;}
    public Double getAvgClickRate(){return avgClickRate;} public void setAvgClickRate(Double avgClickRate){this.avgClickRate=avgClickRate;}
    public Double getAvgCtr(){return avgCtr;} public void setAvgCtr(Double avgCtr){this.avgCtr=avgCtr;}
    public Double getAvgRoyPerEmail(){return avgRoyPerEmail;} public void setAvgRoyPerEmail(Double avgRoyPerEmail){this.avgRoyPerEmail=avgRoyPerEmail;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
