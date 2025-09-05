package com.emailMarketing.roi.repo;

import com.emailMarketing.roi.CostAllocationLink; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;

public interface CostAllocationLinkRepository extends JpaRepository<CostAllocationLink, CostAllocationLink.LinkId> {
    List<CostAllocationLink> findByIdAllocationId(Long allocationId);
}
