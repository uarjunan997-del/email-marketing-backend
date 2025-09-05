package com.emailMarketing.roi;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.*;

@Entity @Table(name="COST_ALLOCATIONS") @Getter @Setter
public class CostAllocation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="RESOURCE_NAME", nullable=false, length=120) private String resourceName;
    @Column(name="ALLOCATION_METHOD", nullable=false, length=40) private String allocationMethod; // HOURS, PERCENTAGE
    @Column(name="BASIS_VALUE", precision=14) private Double basisValue;
    @Column(name="TOTAL_COST", precision=14, nullable=false) private Double totalCost;
    @Column(name="CURRENCY", length=10, nullable=false) private String currency;
    @Column(name="PERIOD_START", nullable=false) private LocalDate periodStart;
    @Column(name="PERIOD_END", nullable=false) private LocalDate periodEnd;
    @Column(name="CREATED_AT") private Instant createdAt=Instant.now();
    @Column(name="ORG_ID") private Long orgId;
}
