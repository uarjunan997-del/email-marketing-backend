package com.emailMarketing.timeseries.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.timeseries.EmailMetricWeekly;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailMetricWeeklyRepository extends JpaRepository<EmailMetricWeekly, EmailMetricWeekly.Key> {
    List<EmailMetricWeekly> findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(Long userId, LocalDateTime from, LocalDateTime to);
}
