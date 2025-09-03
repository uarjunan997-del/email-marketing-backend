package com.yourdomain.controller;

import com.yourdomain.subscription.Subscription;
import com.yourdomain.subscription.SubscriptionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscription")
public class SubscriptionController {
  private final SubscriptionService subscriptionService;
  public SubscriptionController(SubscriptionService subscriptionService){ this.subscriptionService = subscriptionService; }

  public record StartRequest(Long userId, String planType, String billingPeriod){}

  @PostMapping("/start")
  public Subscription start(@RequestBody StartRequest req){
    return subscriptionService.startOrRenew(req.userId(), req.planType(), req.billingPeriod() == null ? "MONTHLY" : req.billingPeriod().toUpperCase());
  }
}
