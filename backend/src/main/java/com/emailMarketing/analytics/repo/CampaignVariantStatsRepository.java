package com.emailMarketing.analytics.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.analytics.CampaignVariantStats;
import java.util.List;

public interface CampaignVariantStatsRepository extends JpaRepository<CampaignVariantStats, Long> {
    List<CampaignVariantStats> findByCampaignId(Long campaignId);
    CampaignVariantStats findByCampaignIdAndVariantCode(Long campaignId, String variantCode);
}
