package com.emailMarketing.template;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    List<EmailTemplate> findByUserId(Long userId);
    List<EmailTemplate> findByIsSharedTrue();

    @Query("select t from EmailTemplate t where t.isShared = true or t.userId = :userId")
    List<EmailTemplate> findAccessibleForUser(@Param("userId") Long userId);
}
