package com.emailMarketing.analytics;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="email_cost_overrides")
public class EmailCostOverride {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private java.sql.Date effectiveDate; private Double costPerEmail; private String currency;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public java.sql.Date getEffectiveDate(){return effectiveDate;} public void setEffectiveDate(java.sql.Date effectiveDate){this.effectiveDate=effectiveDate;}
    public Double getCostPerEmail(){return costPerEmail;} public void setCostPerEmail(Double costPerEmail){this.costPerEmail=costPerEmail;}
    public String getCurrency(){return currency;} public void setCurrency(String currency){this.currency=currency;}
    public LocalDate effectiveLocalDate(){ return effectiveDate==null?null:effectiveDate.toLocalDate(); }
}
