package com.emailMarketing.queue;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailQueueScheduler {
    private final EmailQueueService service;

    public EmailQueueScheduler(EmailQueueService service) {
        this.service = service;
    }

    // Every minute process pending queue (MVP simple schedule)
    @Scheduled(fixedDelay = 60000)
    public void run() {
        service.processPending();
    }
}
