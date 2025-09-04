package com.emailMarketing.analytics;

import java.time.LocalDate;
import java.util.List;

/**
 * Manual (non-Spring-Data) repository used purely for a native aggregation query
 * against the materialized view mv_email_events_daily. We avoid extending a Spring Data
 * Repository with a non-entity domain type (which caused context startup failures).
 */
public interface DailyEmailEventsRepository {
  List<Object[]> aggregateDaily(Long userId, LocalDate since);
}
