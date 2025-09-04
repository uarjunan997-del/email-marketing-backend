package com.emailMarketing.analytics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public class DailyEmailEventsRepositoryImpl implements DailyEmailEventsRepository {
  @PersistenceContext
  private EntityManager em;

  @Override
  public List<Object[]> aggregateDaily(Long userId, LocalDate since) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
        "SELECT event_day, SUM(sent_count), SUM(open_count), SUM(click_count) " +
        "FROM mv_email_events_daily WHERE user_id = :uid AND event_day >= :since GROUP BY event_day ORDER BY event_day")
      .setParameter("uid", userId)
      .setParameter("since", since)
      .getResultList();
    return rows;
  }
}
