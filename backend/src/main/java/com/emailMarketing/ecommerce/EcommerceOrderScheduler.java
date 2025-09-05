package com.emailMarketing.ecommerce;

import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component; import lombok.RequiredArgsConstructor; import org.slf4j.Logger; import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class EcommerceOrderScheduler {
    private static final Logger log = LoggerFactory.getLogger(EcommerceOrderScheduler.class);
    private final EcommerceOrderTransformService service;

    @Scheduled(fixedDelayString = "${ecommerce.transform.delay-ms:30000}")
    public void run(){
        int n = service.processNewRaw(50);
        if(n>0) log.debug("ecommerce_transform_processed count={}", n);
    }
}