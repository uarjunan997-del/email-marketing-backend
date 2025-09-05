package com.emailMarketing.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDateTime;

public interface SendingQueueRepository extends JpaRepository<SendingQueueItem, Long> {
    List<SendingQueueItem> findTop100ByStatusAndNextAttemptAtBeforeOrderByPriorityAscIdAsc(String status, LocalDateTime before);
    long countByCampaignIdAndStatus(Long campaignId, String status);
    List<SendingQueueItem> findByCampaignIdAndStatusIn(Long campaignId, java.util.Collection<String> statuses);
    long countByUserIdAndStatusAndCreatedAtAfter(Long userId, String status, LocalDateTime after);
    long countByStatus(String status);
}
