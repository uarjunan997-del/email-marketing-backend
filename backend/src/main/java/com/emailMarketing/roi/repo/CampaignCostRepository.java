package com.emailMarketing.roi.repo; import com.emailMarketing.roi.CampaignCost; import org.springframework.data.jpa.repository.JpaRepository; import java.time.*; import java.util.*;
public interface CampaignCostRepository extends JpaRepository<CampaignCost, Long> {
	List<CampaignCost> findByCampaignIdAndCostDateBetween(Long campaignId, LocalDate from, LocalDate to);
	List<CampaignCost> findByOrgIdAndCostDateBetween(Long orgId, LocalDate from, LocalDate to);
}
