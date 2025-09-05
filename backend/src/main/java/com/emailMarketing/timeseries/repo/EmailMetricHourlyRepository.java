package com.emailMarketing.timeseries.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.timeseries.EmailMetricHourly;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailMetricHourlyRepository extends JpaRepository<EmailMetricHourly, EmailMetricHourly.Key> {
    List<EmailMetricHourly> findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(Long userId, LocalDateTime from, LocalDateTime to);
}
