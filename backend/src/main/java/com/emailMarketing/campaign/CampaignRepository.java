package com.emailMarketing.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByUserId(Long userId);
    List<Campaign> findByStatusAndScheduledAtBefore(String status, LocalDateTime time);
}
