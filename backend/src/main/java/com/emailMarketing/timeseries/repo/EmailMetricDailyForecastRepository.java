package com.emailMarketing.timeseries.repo;

import com.emailMarketing.timeseries.EmailMetricDailyForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailMetricDailyForecastRepository extends JpaRepository<EmailMetricDailyForecast, EmailMetricDailyForecast.Pk> {
    List<EmailMetricDailyForecast> findByIdUserIdAndIdBucketStartBetweenAndIdModelOrderByIdBucketStartAsc(Long userId, LocalDateTime from, LocalDateTime to, String model);
}