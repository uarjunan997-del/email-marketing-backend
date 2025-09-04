package com.emailMarketing.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {
  // Using native queries with Oracle hints for large datasets
  @Query(value = "SELECT /*+ PARALLEL(4) FULL(e) */ e.event_type, COUNT(*) cnt FROM email_events e WHERE e.user_id=:uid AND e.event_time >= :since GROUP BY e.event_type", nativeQuery = true)
  List<Object[]> aggregateEventsSince(@Param("uid") Long userId, @Param("since") LocalDateTime since);

  @Query(value = "SELECT /*+ INDEX(e idx_ev_user) */ TRUNC(e.event_time) d, e.event_type, COUNT(*) FROM email_events e WHERE e.user_id=:uid AND e.event_time >= :since GROUP BY TRUNC(e.event_time), e.event_type ORDER BY TRUNC(e.event_time)", nativeQuery = true)
  List<Object[]> dailyBreakdown(@Param("uid") Long userId, @Param("since") LocalDateTime since);

  @Query(value = "SELECT /*+ PARALLEL(2) */ e.campaign_id, SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END) opens, SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END) clicks FROM email_events e WHERE e.user_id=:uid GROUP BY e.campaign_id", nativeQuery = true)
  List<Object[]> campaignEngagement(@Param("uid") Long userId);
}
