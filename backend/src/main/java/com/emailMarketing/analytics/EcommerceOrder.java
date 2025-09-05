package com.emailMarketing.analytics;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="ecommerce_orders")
public class EcommerceOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private String externalOrderId; private String currency; private Double totalAmount; private Double subtotalAmount; private Double taxAmount; private Double shippingAmount; private LocalDateTime createdAt; private String customerEmail;
    @Lob private String jsonPayload;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getExternalOrderId(){return externalOrderId;} public void setExternalOrderId(String externalOrderId){this.externalOrderId=externalOrderId;}
    public String getCurrency(){return currency;} public void setCurrency(String currency){this.currency=currency;}
    public Double getTotalAmount(){return totalAmount;} public void setTotalAmount(Double totalAmount){this.totalAmount=totalAmount;}
    public Double getSubtotalAmount(){return subtotalAmount;} public void setSubtotalAmount(Double subtotalAmount){this.subtotalAmount=subtotalAmount;}
    public Double getTaxAmount(){return taxAmount;} public void setTaxAmount(Double taxAmount){this.taxAmount=taxAmount;}
    public Double getShippingAmount(){return shippingAmount;} public void setShippingAmount(Double shippingAmount){this.shippingAmount=shippingAmount;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public String getCustomerEmail(){return customerEmail;} public void setCustomerEmail(String customerEmail){this.customerEmail=customerEmail;}
    public String getJsonPayload(){return jsonPayload;} public void setJsonPayload(String jsonPayload){this.jsonPayload=jsonPayload;}
}
