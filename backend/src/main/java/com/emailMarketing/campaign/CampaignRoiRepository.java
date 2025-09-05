package com.emailMarketing.campaign;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRoiRepository extends JpaRepository<CampaignRoi, Long> {
    CampaignRoi findByCampaignId(Long campaignId);
}
