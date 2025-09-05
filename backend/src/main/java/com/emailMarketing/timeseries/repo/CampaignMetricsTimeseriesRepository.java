package com.emailMarketing.timeseries.repo;

import com.emailMarketing.timeseries.CampaignMetricsTimeseries;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface CampaignMetricsTimeseriesRepository extends JpaRepository<CampaignMetricsTimeseries, CampaignMetricsTimeseries.Key> {
    List<CampaignMetricsTimeseries> findByIdCampaignIdAndIdMetricDateBetweenOrderByIdMetricDateAsc(Long campaignId, LocalDate from, LocalDate to);
}
