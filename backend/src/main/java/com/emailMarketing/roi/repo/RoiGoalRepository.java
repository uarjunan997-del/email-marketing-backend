package com.emailMarketing.roi.repo; import com.emailMarketing.roi.RoiGoal; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RoiGoalRepository extends JpaRepository<RoiGoal, Long> { Optional<RoiGoal> findByCampaignId(Long campaignId); }
