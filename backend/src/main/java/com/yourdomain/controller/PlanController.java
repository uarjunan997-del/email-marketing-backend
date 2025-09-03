package com.yourdomain.controller;

import com.yourdomain.subscription.Plan;
import com.yourdomain.subscription.PlanRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/public/plans")
public class PlanController {
  private final PlanRepository planRepository;
  public PlanController(PlanRepository planRepository){ this.planRepository = planRepository; }

  @GetMapping
  public List<Plan> list(@RequestParam(required=false) String region, @RequestParam(required=false) String billingPeriod){
    if(region != null && billingPeriod != null){ return planRepository.findByRegionAndBillingPeriod(region, billingPeriod); }
    if(region != null){ return planRepository.findByRegion(region); }
    return planRepository.findAll();
  }
}
