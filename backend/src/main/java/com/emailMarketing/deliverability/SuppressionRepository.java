package com.emailMarketing.deliverability;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SuppressionRepository extends JpaRepository<SuppressionEntry, Long> {
    boolean existsByUserIdAndEmail(Long userId, String email);
}
