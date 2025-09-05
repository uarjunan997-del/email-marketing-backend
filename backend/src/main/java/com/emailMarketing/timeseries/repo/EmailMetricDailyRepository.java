package com.emailMarketing.timeseries.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.timeseries.EmailMetricDaily;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailMetricDailyRepository extends JpaRepository<EmailMetricDaily, EmailMetricDaily.Key> {
    List<EmailMetricDaily> findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(Long userId, LocalDateTime from, LocalDateTime to);
}
