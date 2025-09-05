package com.emailMarketing.benchmark.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.benchmark.BenchmarkComparison;
import java.util.List;

public interface BenchmarkComparisonRepository extends JpaRepository<BenchmarkComparison, Long> {
    List<BenchmarkComparison> findTop30ByUserIdOrderByComputedAtDesc(Long userId);
}
