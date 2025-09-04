package com.emailMarketing.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByUserId(Long userId);
    List<Contact> findByUserIdAndSegment(Long userId, String segment);
    long countByUserIdAndUnsubscribedFalse(Long userId);
    long countByUserId(Long userId);
    long countByUserIdAndUnsubscribedTrue(Long userId);
}
