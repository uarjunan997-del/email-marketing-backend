package com.yourdomain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Long> {
  Optional<Plan> findByPlanType(String planType);
  Optional<Plan> findByPlanTypeAndCurrencyAndBillingPeriod(String planType, String currency, String billingPeriod);
  List<Plan> findByRegion(String region);
  List<Plan> findByRegionAndBillingPeriod(String region, String billingPeriod);
}
