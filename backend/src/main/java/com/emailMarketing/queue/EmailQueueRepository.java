package com.emailMarketing.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailQueueRepository extends JpaRepository<EmailQueueItem, Long> {
    List<EmailQueueItem> findTop50ByStatusAndNextAttemptAtBeforeOrderByIdAsc(String status, LocalDateTime time);
}
