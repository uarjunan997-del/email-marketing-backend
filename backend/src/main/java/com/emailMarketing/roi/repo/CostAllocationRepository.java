package com.emailMarketing.roi.repo;

import com.emailMarketing.roi.CostAllocation; import org.springframework.data.jpa.repository.JpaRepository; import java.time.LocalDate; import java.util.List;

public interface CostAllocationRepository extends JpaRepository<CostAllocation, Long> {
    List<CostAllocation> findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(LocalDate dayStart, LocalDate dayEnd);
    List<CostAllocation> findByOrgIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(Long orgId, LocalDate dayStart, LocalDate dayEnd);
}
