package com.emailMarketing.timeseries.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.timeseries.EmailMetricMonthly;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailMetricMonthlyRepository extends JpaRepository<EmailMetricMonthly, EmailMetricMonthly.Key> {
    List<EmailMetricMonthly> findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(Long userId, LocalDateTime from, LocalDateTime to);
}
