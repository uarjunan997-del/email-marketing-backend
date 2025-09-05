package com.emailMarketing.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignScheduleRepository extends JpaRepository<CampaignSchedule, Long> {
    List<CampaignSchedule> findByScheduledTimeBeforeAndScheduledTimeAfter(java.time.LocalDateTime before, java.time.LocalDateTime after);
}
