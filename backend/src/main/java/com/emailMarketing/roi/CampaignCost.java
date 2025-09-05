package com.emailMarketing.roi;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.*;

@Entity @Table(name="CAMPAIGN_COSTS") @Getter @Setter
public class CampaignCost {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="CAMPAIGN_ID", nullable=false)
    private Long campaignId;
    @Column(name="CATEGORY_CODE", nullable=false, length=50)
    private String categoryCode;
    @Column(name="SUBCATEGORY", length=100)
    private String subcategory;
    @Column(name="COST_DATE", nullable=false)
    private LocalDate costDate;
    @Column(name="AMOUNT", nullable=false, precision=14)
    private Double amount;
    @Column(name="CURRENCY", nullable=false, length=10)
    private String currency;
    @Column(name="QUANTITY", precision=12)
    private Double quantity;
    @Column(name="UNIT_COST", precision=14)
    private Double unitCost;
    @Column(name="NOTES", length=400)
    private String notes;
    @Column(name="ORG_ID")
    private Long orgId;
    @Column(name="CREATED_AT")
    private java.time.Instant createdAt = java.time.Instant.now();
}
