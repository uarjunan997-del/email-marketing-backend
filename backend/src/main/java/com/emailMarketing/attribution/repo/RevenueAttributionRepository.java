package com.emailMarketing.attribution.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.attribution.RevenueAttribution;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevenueAttributionRepository extends JpaRepository<RevenueAttribution, Long> {
    List<RevenueAttribution> findByCampaignIdAndModelCode(Long campaignId, String modelCode);
    List<RevenueAttribution> findByOrderId(Long orderId);
    List<RevenueAttribution> findByUserId(Long userId);
    List<RevenueAttribution> findByUserIdAndModelCode(Long userId, String modelCode);

    @Query("select distinct r.orderId from RevenueAttribution r where r.userId=:uid and r.modelCode=:model")
    List<Long> distinctOrderIdsForUserAndModel(@Param("uid") Long userId, @Param("model") String modelCode);
}
