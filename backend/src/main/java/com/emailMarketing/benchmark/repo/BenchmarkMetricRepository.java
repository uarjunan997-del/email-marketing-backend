package com.emailMarketing.benchmark.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.benchmark.BenchmarkMetric;
import java.util.List;

public interface BenchmarkMetricRepository extends JpaRepository<BenchmarkMetric, Long> {
    BenchmarkMetric findByIndustryAndListTierAndRegion(String industry, String listTier, String region);
    List<BenchmarkMetric> findByIndustry(String industry);
    List<BenchmarkMetric> findDistinctByRegion(String region);
}
