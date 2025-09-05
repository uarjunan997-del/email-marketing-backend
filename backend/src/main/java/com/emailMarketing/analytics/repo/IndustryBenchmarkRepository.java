package com.emailMarketing.analytics.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.analytics.IndustryBenchmark;

public interface IndustryBenchmarkRepository extends JpaRepository<IndustryBenchmark, Long> {
    IndustryBenchmark findByVerticalAndListTier(String vertical, String listTier);
}
