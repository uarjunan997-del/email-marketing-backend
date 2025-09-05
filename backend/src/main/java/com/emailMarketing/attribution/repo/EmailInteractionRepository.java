package com.emailMarketing.attribution.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.attribution.EmailInteraction;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailInteractionRepository extends JpaRepository<EmailInteraction, Long> {
    List<EmailInteraction> findByUserIdAndEmailAndEventTimeAfter(Long userId, String email, LocalDateTime after);
    List<EmailInteraction> findByUserIdAndCampaignIdAndEventTimeBetween(Long userId, Long campaignId, LocalDateTime from, LocalDateTime to);
}
