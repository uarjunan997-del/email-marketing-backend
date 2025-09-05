package com.emailMarketing.benchmark;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="user_industry_classification")
public class UserIndustryClassification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private String industry; private Double confidence; private String method; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getIndustry(){return industry;} public void setIndustry(String industry){this.industry=industry;}
    public Double getConfidence(){return confidence;} public void setConfidence(Double confidence){this.confidence=confidence;}
    public String getMethod(){return method;} public void setMethod(String method){this.method=method;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
