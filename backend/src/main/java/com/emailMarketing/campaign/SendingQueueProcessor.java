package com.emailMarketing.campaign;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.slf4j.MDC;

@Component
public class SendingQueueProcessor {
    private static final Logger log = LoggerFactory.getLogger(SendingQueueProcessor.class);
    private final CampaignService campaignService; private final CampaignRecipientRepository recipientRepository; private final com.emailMarketing.email.FailoverEmailService failoverEmailService; private final com.emailMarketing.metrics.EmailMetrics emailMetrics;
    public SendingQueueProcessor(CampaignService campaignService, CampaignRecipientRepository recipientRepository, com.emailMarketing.email.FailoverEmailService failoverEmailService, com.emailMarketing.metrics.EmailMetrics emailMetrics){ this.campaignService=campaignService; this.recipientRepository=recipientRepository; this.failoverEmailService=failoverEmailService; this.emailMetrics=emailMetrics; }

    // Runs every 15s; adjust throttle strategy later.
    @Scheduled(fixedDelay = 15000, initialDelay = 10000)
    @Transactional
    public void processBatch(){
        campaignService.processQueueBatch((item, ok)->{
            MDC.put("campaignId", String.valueOf(item.getCampaignId()));
            MDC.put("queueItemId", String.valueOf(item.getId()));
            MDC.put("recipient", item.getRecipient());
            try {
                var result = failoverEmailService.send(item.getRecipient(), item.getSubject(), item.getBody());
                var rec = recipientRepository.findFirstByCampaignIdAndEmail(item.getCampaignId(), item.getRecipient());
                switch(result.type()){
                    case SUCCESS -> { if(rec!=null && "QUEUED".equals(rec.getStatus())) rec.setStatus("SENT"); emailMetrics.increment("provider_send","SUCCESS"); log.debug("send_success provider={} subject='{}'", result.provider(), item.getSubject()); }
                    case CONFIG_ERROR, PERMANENT_FAILURE -> { if(rec!=null) rec.setStatus("FAILED"); emailMetrics.increment("provider_send", result.type().name()); log.warn("send_permanent_failure provider={} msg={}", result.provider(), result.message()); throw new PermanentEmailSendException("Permanent failure: "+result.message()); }
                    case TRANSIENT_FAILURE -> {
                        if(rec!=null) rec.setStatus("QUEUED");
                        emailMetrics.increment("provider_send", "TRANSIENT_FAILURE");
                        log.info("send_transient_failure provider={} msg={}", result.provider(), result.message());
                        throw new TransientEmailSendException("Transient failure: "+result.message());
                    }
                }
            } finally {
                MDC.clear();
            }
        });
        // Stats recalculation could be triggered here in future.
    }
}
