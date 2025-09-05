package com.emailMarketing.analytics.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.analytics.EcommerceOrder;
import java.time.LocalDateTime;
import java.util.List;

public interface EcommerceOrderRepository extends JpaRepository<EcommerceOrder, Long> {
    EcommerceOrder findByUserIdAndExternalOrderId(Long userId, String externalOrderId);
    List<EcommerceOrder> findByUserIdAndCustomerEmailAndCreatedAtAfter(Long userId, String customerEmail, LocalDateTime after);
    List<EcommerceOrder> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);
}
