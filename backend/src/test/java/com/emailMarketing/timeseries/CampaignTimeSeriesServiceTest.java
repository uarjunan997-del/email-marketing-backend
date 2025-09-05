package com.emailMarketing.timeseries;

import com.emailMarketing.timeseries.repo.CampaignMetricsTimeseriesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignTimeSeriesServiceTest {

    @Test
    void decomposition_returnsSeasonalFactors(){
        CampaignMetricsTimeseriesRepository repo = mock(CampaignMetricsTimeseriesRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CampaignTimeSeriesService svc = new CampaignTimeSeriesService(repo, jdbc, 0.15,20,25,50);
        List<CampaignMetricsTimeseries> raw = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(40);
        for(int i=0;i<40;i++){
            LocalDate d = start.plusDays(i);
            for(int h=0; h<1; h++){ // 1 row per day for simplicity
                CampaignMetricsTimeseries r = new CampaignMetricsTimeseries();
                CampaignMetricsTimeseries.Key k = new CampaignMetricsTimeseries.Key();
                k.setCampaignId(1L); k.setMetricDate(d); k.setMetricHour(0); r.setId(k);
                long sent = 100; long delivered=95; long opened = 15 + (i%7); // weekly seasonality
                r.setSentCount(sent); r.setDeliveredCount(delivered); r.setOpenedCount(opened); r.setClickedCount(5L); r.setConvertedCount(2L); r.setRevenue(10.0);
                raw.add(r);
            }
        }
        when(repo.findByIdCampaignIdAndIdMetricDateBetweenOrderByIdMetricDateAsc(anyLong(), any(), any())).thenReturn(raw);
        var trend = svc.trendAnalysis(1L, 40);
        @SuppressWarnings("unchecked") Map<String,Object> decomposition = (Map<String,Object>) trend.get("decomposition");
        assertEquals("OK", decomposition.get("status"));
        assertTrue(((List<?>)decomposition.get("seasonalFactors")).size()>=7);
    }

    @Test
    void alerts_triggerDifferentRules(){
        CampaignMetricsTimeseriesRepository repo = mock(CampaignMetricsTimeseriesRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CampaignTimeSeriesService svc = new CampaignTimeSeriesService(repo, jdbc, 0.15,20,25,50);
        List<CampaignMetricsTimeseries> raw = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(60);
        for(int i=0;i<60;i++){
            LocalDate d = start.plusDays(i);
            CampaignMetricsTimeseries r = new CampaignMetricsTimeseries();
            CampaignMetricsTimeseries.Key k = new CampaignMetricsTimeseries.Key();
            k.setCampaignId(2L); k.setMetricDate(d); k.setMetricHour(0); r.setId(k);
            long delivered=100;
            long opened = (i<55)?30:5; // last few days drop
            long clicked = (i<55)?15:2; // drop
            long converted = (i<55)?4:8; // spike
            r.setSentCount(delivered); r.setDeliveredCount(delivered); r.setOpenedCount(opened); r.setClickedCount(clicked); r.setConvertedCount(converted); r.setRevenue(20.0);
            raw.add(r);
        }
        when(repo.findByIdCampaignIdAndIdMetricDateBetweenOrderByIdMetricDateAsc(anyLong(), any(), any())).thenReturn(raw);
        var res = svc.performanceAlerts(2L, 60, 0.2);
        assertEquals("OK", res.get("status"));
        @SuppressWarnings("unchecked") List<String> alerts = (List<String>) res.get("alerts");
        assertTrue(alerts.stream().anyMatch(a->a.startsWith("OPEN_RATE_DROP")));
        assertTrue(alerts.stream().anyMatch(a->a.startsWith("CLICK_RATE_DROP")));
        assertTrue(alerts.stream().anyMatch(a->a.startsWith("CONVERSION_SPIKE")));
    }
}
