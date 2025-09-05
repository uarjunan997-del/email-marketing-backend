package com.emailMarketing.deliverability;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailComplaintRepository extends JpaRepository<EmailComplaint, Long> {
    boolean existsByUserIdAndEmail(Long userId, String email);
}
