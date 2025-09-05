package com.emailMarketing.roi;

import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;

@Component
public class ExchangeRateScheduler {
    private final ExchangeRateIngestionService svc;
    public ExchangeRateScheduler(ExchangeRateIngestionService s){ this.svc=s; }
    @Scheduled(cron="0 10 0 * * *")
    public void nightly(){ svc.ingestLatest(); }
}
