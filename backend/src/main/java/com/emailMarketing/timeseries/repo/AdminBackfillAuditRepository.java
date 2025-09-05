package com.emailMarketing.timeseries.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.timeseries.AdminBackfillAudit;
import java.util.List;

public interface AdminBackfillAuditRepository extends JpaRepository<AdminBackfillAudit, Long> {
    List<AdminBackfillAudit> findTop50ByOrderByTriggeredAtDesc();
}