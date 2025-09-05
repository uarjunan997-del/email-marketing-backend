package com.emailMarketing.attribution;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="attribution_fraud_flags")
public class AttributionFraudFlag {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private Long orderId; private String reasonCode; @Lob private String details; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public Long getOrderId(){return orderId;} public void setOrderId(Long orderId){this.orderId=orderId;}
    public String getReasonCode(){return reasonCode;} public void setReasonCode(String reasonCode){this.reasonCode=reasonCode;}
    public String getDetails(){return details;} public void setDetails(String details){this.details=details;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
