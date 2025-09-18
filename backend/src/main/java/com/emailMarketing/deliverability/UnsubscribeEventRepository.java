package com.emailMarketing.deliverability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface UnsubscribeEventRepository extends JpaRepository<UnsubscribeEvent, Long> {
  long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
  List<UnsubscribeEvent> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}
