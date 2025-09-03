package com.yourdomain.subscription;

import jakarta.persistence.*;

@Entity
@Table(name="plans", uniqueConstraints = @UniqueConstraint(columnNames = {"plan_type","region","billing_period"}))
public class Plan {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="plan_type", nullable=false) private String planType; // FREE, PRO, PREMIUM
  @Column(nullable=false) private String region;
  @Column(nullable=false) private String currency;
  @Column(name="billing_period", nullable=false, length=16) private String billingPeriod = "MONTHLY"; // MONTHLY|YEARLY
  private int amount; // minor units (e.g. cents/paise)
  private int statementsPerMonth;
  private int pagesPerStatement;
  private String features; // JSON or CSV string
  private Integer combinedBank;
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public String getPlanType(){return planType;} public void setPlanType(String planType){this.planType=planType;}
  public String getRegion(){return region;} public void setRegion(String region){this.region=region;}
  public String getCurrency(){return currency;} public void setCurrency(String currency){this.currency=currency;}
  public String getBillingPeriod(){return billingPeriod;} public void setBillingPeriod(String billingPeriod){this.billingPeriod=billingPeriod;}
  public int getAmount(){return amount;} public void setAmount(int amount){this.amount=amount;}
  public int getStatementsPerMonth(){return statementsPerMonth;} public void setStatementsPerMonth(int v){this.statementsPerMonth=v;}
  public int getPagesPerStatement(){return pagesPerStatement;} public void setPagesPerStatement(int v){this.pagesPerStatement=v;}
  public String getFeatures(){return features;} public void setFeatures(String features){this.features=features;}
  public Integer getCombinedBank(){return combinedBank;} public void setCombinedBank(Integer combinedBank){this.combinedBank=combinedBank;}
}
