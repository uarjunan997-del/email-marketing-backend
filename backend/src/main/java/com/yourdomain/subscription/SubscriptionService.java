package com.yourdomain.subscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class SubscriptionService {
  private final SubscriptionRepository subscriptionRepository;
  private final PlanRepository planRepository;
  private final UserRepository userRepository;

  public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository, UserRepository userRepository) {
    this.subscriptionRepository = subscriptionRepository; this.planRepository = planRepository; this.userRepository = userRepository;
  }

  private static final Set<String> VALID_PERIODS = Set.of("MONTHLY","YEARLY");

  @Transactional
  public Subscription startOrRenew(Long userId, String planType, String billingPeriod) {
    if(billingPeriod == null) billingPeriod = "MONTHLY";
    billingPeriod = billingPeriod.toUpperCase();
    if(!VALID_PERIODS.contains(billingPeriod)) throw new IllegalArgumentException("Invalid billing period");
    var user = userRepository.findById(userId).orElseThrow();
    // Ensure plan exists for this user's currency + period; retrieving unused object to validate.
    planRepository.findByPlanTypeAndCurrencyAndBillingPeriod(planType, user.getCurrency(), billingPeriod).orElseThrow();
    var sub = subscriptionRepository.findByUserId(userId).orElseGet(Subscription::new);
    sub.setUser(user);
    sub.setPlanType(Subscription.PlanType.valueOf(planType));
    sub.setStatus("ACTIVE");
    sub.setBillingPeriod(billingPeriod);
    sub.setStartDate(LocalDateTime.now());
    sub.setEndDate("YEARLY".equals(billingPeriod) ? LocalDateTime.now().plusYears(1) : LocalDateTime.now().plusMonths(1));
    return subscriptionRepository.save(sub);
  }

  public Subscription current(Long userId){
    return subscriptionRepository.findByUserId(userId).orElse(null);
  }
}
