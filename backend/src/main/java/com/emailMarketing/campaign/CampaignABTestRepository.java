package com.emailMarketing.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignABTestRepository extends JpaRepository<CampaignABTest, Long> {
    List<CampaignABTest> findByCampaignId(Long campaignId);
}
