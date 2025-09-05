package com.emailMarketing.analytics.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.analytics.EmailCostOverride;
import java.util.List;

public interface EmailCostOverrideRepository extends JpaRepository<EmailCostOverride, Long> {
    List<EmailCostOverride> findTop1ByUserIdOrderByEffectiveDateDesc(Long userId);
}
