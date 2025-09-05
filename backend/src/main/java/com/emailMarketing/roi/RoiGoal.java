package com.emailMarketing.roi;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.*;

@Entity @Table(name="ROI_GOALS") @Getter @Setter
public class RoiGoal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="CAMPAIGN_ID", nullable=false, unique=true) private Long campaignId;
    @Column(name="TARGET_ROI_PCT", precision=9) private Double targetRoiPct;
    @Column(name="TARGET_MARGIN_PCT", precision=9) private Double targetMarginPct;
    @Column(name="CREATED_AT") private Instant createdAt = Instant.now();
    @Column(name="UPDATED_AT") private Instant updatedAt;
    @Column(name="ORG_ID") private Long orgId;
}
