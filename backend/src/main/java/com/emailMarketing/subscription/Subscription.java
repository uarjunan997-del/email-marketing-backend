package com.emailMarketing.subscription;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="subscriptions")
public class Subscription {
  public enum PlanType { FREE, PRO, PREMIUM }
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Enumerated(EnumType.STRING) private PlanType planType;
  private LocalDateTime startDate; private LocalDateTime endDate; private String status; // ACTIVE,PENDING,FAILED,CANCELLED
  private String externalPaymentId; private String externalOrderId; private String billingPeriod; // MONTHLY|YEARLY
  @OneToOne @JoinColumn(name="user_id", unique = true) private User user;
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public PlanType getPlanType(){return planType;} public void setPlanType(PlanType p){this.planType=p;}
  public LocalDateTime getStartDate(){return startDate;} public void setStartDate(LocalDateTime s){this.startDate=s;}
  public LocalDateTime getEndDate(){return endDate;} public void setEndDate(LocalDateTime e){this.endDate=e;}
  public String getStatus(){return status;} public void setStatus(String s){this.status=s;}
  public String getExternalPaymentId(){return externalPaymentId;} public void setExternalPaymentId(String v){this.externalPaymentId=v;}
  public String getExternalOrderId(){return externalOrderId;} public void setExternalOrderId(String v){this.externalOrderId=v;}
  public String getBillingPeriod(){return billingPeriod;} public void setBillingPeriod(String v){this.billingPeriod=v;}
  public User getUser(){return user;} public void setUser(User u){this.user=u;}
}
