package com.emailMarketing.timeseries;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

@Component
public class CampaignTimeSeriesScheduler {
    private final CampaignTimeSeriesService service; private final List<Long> campaignIds; private final JdbcTemplate jdbc;
    public CampaignTimeSeriesScheduler(CampaignTimeSeriesService s, JdbcTemplate jdbc,
        @Value("${timeseries.cache.warm.campaign-ids:}") String ids){
        this.service=s; this.jdbc=jdbc; if(ids==null || ids.isBlank()) this.campaignIds=List.of(); else this.campaignIds = Arrays.stream(ids.split(",")).filter(v->!v.isBlank()).map(Long::valueOf).toList(); }

    // Refresh materialized view & warm caches periodically (every refresh-ms)
    @Scheduled(fixedDelayString = "${timeseries.mv.refresh-ms:300000}")
    public void refreshAndWarm(){
        try {
            jdbc.execute("BEGIN DBMS_MVIEW.REFRESH('CAMPAIGN_METRICS_DAILY','F'); END;");
        } catch(Exception ignored){ }
        for(Long id: campaignIds){
            try {
                service.trendAnalysis(id, 60); // warms trending + decomposition
                service.performanceAlerts(id, 30, 0.15);
                service.anomalies(id, 60);
            } catch(Exception ignored){ }
        }
    }
}
