package com.emailMarketing.benchmark.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.benchmark.UserIndustryClassification;
import java.util.List;

public interface UserIndustryClassificationRepository extends JpaRepository<UserIndustryClassification, Long> {
    List<UserIndustryClassification> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);
}
