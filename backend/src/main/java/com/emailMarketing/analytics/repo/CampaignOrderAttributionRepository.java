package com.emailMarketing.analytics.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.analytics.CampaignOrderAttribution;
import java.util.List;

public interface CampaignOrderAttributionRepository extends JpaRepository<CampaignOrderAttribution, Long> {
    List<CampaignOrderAttribution> findByCampaignId(Long campaignId);
    List<CampaignOrderAttribution> findByUserIdAndCampaignId(Long userId, Long campaignId);
}
