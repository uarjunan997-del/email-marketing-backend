package com.emailMarketing.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignApprovalRepository extends JpaRepository<CampaignApproval, Long> {
    List<CampaignApproval> findByCampaignId(Long campaignId);
}
