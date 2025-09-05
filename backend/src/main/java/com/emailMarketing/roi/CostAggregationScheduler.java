package com.emailMarketing.roi;

import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component; import java.time.*;

@Component
public class CostAggregationScheduler {
    private final CostAggregationService svc;
    public CostAggregationScheduler(CostAggregationService s){ this.svc=s; }
    @Scheduled(cron="0 20 0 * * *")
    public void nightly(){ svc.aggregateDay(LocalDate.now().minusDays(1)); }
}
