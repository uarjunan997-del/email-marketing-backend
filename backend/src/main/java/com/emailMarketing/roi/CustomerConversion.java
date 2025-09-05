package com.emailMarketing.roi;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.*;

@Entity @Table(name="CUSTOMER_CONVERSIONS") @Getter @Setter
public class CustomerConversion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="CAMPAIGN_ID", nullable=false) private Long campaignId;
    @Column(name="CUSTOMER_ID", nullable=false, length=100) private String customerId;
    @Column(name="CONVERSION_DATE", nullable=false) private LocalDate conversionDate;
    @Column(name="REVENUE_AMOUNT", nullable=false) private Double revenueAmount;
    @Column(name="LIFETIME_VALUE") private Double lifetimeValue;
    @Column(name="CREATED_AT") private Instant createdAt = Instant.now();
}
