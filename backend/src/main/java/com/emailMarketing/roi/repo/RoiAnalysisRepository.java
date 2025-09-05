package com.emailMarketing.roi.repo; import com.emailMarketing.roi.RoiAnalysis; import org.springframework.data.jpa.repository.JpaRepository; import java.time.*; import java.util.*;
public interface RoiAnalysisRepository extends JpaRepository<RoiAnalysis, Long> { Optional<RoiAnalysis> findByCampaignIdAndAnalysisDate(Long campaignId, LocalDate date); }
