package com.emailMarketing.contact;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;


@Component
public class ImportProcessor {
    public ImportProcessor(JdbcTemplate jdbc){ /* no-op */ }

    // Disabled: use ImportStagingProcessor for actual processing
    @Scheduled(fixedDelayString = "60000")
    @Transactional
    public void pollAndProcess() {
        // no-op to avoid conflicting processors
    }
}
