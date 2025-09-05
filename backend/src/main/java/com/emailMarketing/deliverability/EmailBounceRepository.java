package com.emailMarketing.deliverability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmailBounceRepository extends JpaRepository<EmailBounce, Long> {
    boolean existsByUserIdAndEmail(Long userId, String email);
    List<EmailBounce> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}
