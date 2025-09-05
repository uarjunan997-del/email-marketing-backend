package com.emailMarketing.roi;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.*;

@Entity @Table(name="ROI_ANALYSIS") @Getter @Setter
public class RoiAnalysis {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="CAMPAIGN_ID", nullable=false) private Long campaignId;
    @Column(name="ANALYSIS_DATE", nullable=false) private LocalDate analysisDate;
    @Column(name="BASE_CURRENCY", nullable=false, length=10) private String baseCurrency;
    @Column(name="TOTAL_COST_BASE", nullable=false) private Double totalCostBase;
    @Column(name="TOTAL_REVENUE_BASE", nullable=false) private Double totalRevenueBase;
    @Column(name="ROI_PCT", nullable=false) private Double roiPct;
    @Column(name="PROFIT_MARGIN_PCT") private Double profitMarginPct;
    @Column(name="PAYBACK_DAYS") private Double paybackDays;
    @Column(name="LTV_ATTRIBUTED") private Double ltvAttributed;
    @Column(name="GENERATED_AT") private Instant generatedAt = Instant.now();
}
