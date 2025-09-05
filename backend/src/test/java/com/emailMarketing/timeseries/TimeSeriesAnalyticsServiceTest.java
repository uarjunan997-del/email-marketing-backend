package com.emailMarketing.timeseries;

import com.emailMarketing.timeseries.repo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSeriesAnalyticsServiceTest {

    @Mock EmailMetricHourlyRepository hourlyRepo; @Mock EmailMetricDailyRepository dailyRepo; @Mock EmailMetricWeeklyRepository weeklyRepo; @Mock EmailMetricMonthlyRepository monthlyRepo; @Mock EmailMetricDailyForecastRepository forecastRepo;

    private TimeSeriesAnalyticsService spyService(){
        TimeSeriesAnalyticsService svc = new TimeSeriesAnalyticsService(hourlyRepo, dailyRepo, weeklyRepo, monthlyRepo, forecastRepo, 0.3,0.05,0.2);
        return Mockito.spy(svc);
    }

    private List<TimeSeriesAnalyticsService.Point> buildSeries(int days, java.util.function.IntToDoubleFunction openRateFn){
        List<TimeSeriesAnalyticsService.Point> list = new ArrayList<>();
        LocalDateTime anchor = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).minusDays(days-1);
        for(int i=0;i<days;i++){
            LocalDateTime bucket = anchor.plusDays(i);
            long sent = 100;
            double or = openRateFn.applyAsDouble(i);
            long open = Math.round(sent * or);
            list.add(new TimeSeriesAnalyticsService.Point(bucket, sent, open, 0,0,0,0));
        }
        return list;
    }

    private TimeSeriesAnalyticsService.TrendResponse wrap(List<TimeSeriesAnalyticsService.Point> pts){
        return new TimeSeriesAnalyticsService.TrendResponse(pts, Map.of(), Map.of(), Map.of());
    }

    @Test
    void forecast_horizonTrim_and_persist(){
        TimeSeriesAnalyticsService svc = spyService();
        List<TimeSeriesAnalyticsService.Point> pts = buildSeries(120, i-> 0.20 + (i%7)*0.01); // weekly seasonal variation
        doReturn(wrap(pts)).when(svc).daily(1L,120);
        Map<String,Object> result = svc.forecast(1L, 45); // request >30
        assertEquals("OK", result.get("status"));
        assertEquals(30, result.get("horizon"));
        @SuppressWarnings("unchecked") List<Map<String,Object>> fpts = (List<Map<String,Object>>) result.get("points");
        assertEquals(30, fpts.size());
    // assert range 0..1
    for(var m: fpts){ double v = (double) m.get("openRateForecast"); assertTrue(v>=0 && v<=1, "Forecast outside [0,1]"); }
        final int[] counter = {0};
    doAnswer(inv-> { Iterable<EmailMetricDailyForecast> it = inv.getArgument(0); for(@SuppressWarnings("unused") EmailMetricDailyForecast ignored: it) counter[0]++; return null; })
            .when(forecastRepo).saveAll(any());
        // call again to trigger mocked saveAll (need fresh invocation)
        svc.forecast(1L,45);
        assertEquals(30, counter[0], "Should persist one row per horizon day");
    }

    @Test
    void anomalies_insufficientData() {
        TimeSeriesAnalyticsService svc = spyService();
        List<TimeSeriesAnalyticsService.Point> pts = buildSeries(10, i->0.2);
        doReturn(wrap(pts)).when(svc).daily(5L,10);
        Map<String,Object> res = svc.anomalies(5L,10);
        assertEquals("INSUFFICIENT_DATA", res.get("status"));
    }

    @Test
    void anomalies_detectsOutlier(){
        TimeSeriesAnalyticsService svc = spyService();
        List<TimeSeriesAnalyticsService.Point> pts = buildSeries(30, i-> i==15?0.5:0.2);
        doReturn(wrap(pts)).when(svc).daily(7L,30);
        Map<String,Object> res = svc.anomalies(7L,30);
        assertEquals("OK", res.get("status"));
        @SuppressWarnings("unchecked") List<Map<String,Object>> list = (List<Map<String,Object>>) res.get("anomalies");
        assertEquals(1, list.size(), "Should flag single anomaly");
    }

    @Test
    void anomalies_precisionRecallSynthetic(){
        TimeSeriesAnalyticsService svc = spyService();
        // Build 56 days baseline 0.2 with 4 injected anomalies at high rate 0.5
        Set<Integer> anomalyIdx = Set.of(10, 25, 40, 55-1); // some spread
        List<TimeSeriesAnalyticsService.Point> pts = buildSeries(56, i-> anomalyIdx.contains(i)?0.5:0.2);
        doReturn(wrap(pts)).when(svc).daily(11L,56);
        Map<String,Object> res = svc.anomalies(11L,56);
        assertEquals("OK", res.get("status"));
        @SuppressWarnings("unchecked") List<Map<String,Object>> detected = (List<Map<String,Object>>) res.get("anomalies");
        // Map detected buckets back to index by matching timestamp order (buildSeries produced sequential days)
        Map<LocalDateTime,Integer> indexMap = new HashMap<>();
        for(int i=0;i<pts.size();i++){ indexMap.put(pts.get(i).bucket(), i); }
        int tp=0, fp=0; for(var a: detected){ LocalDateTime b = LocalDateTime.parse((String)a.get("bucket")); if(anomalyIdx.contains(indexMap.get(b))) tp++; else fp++; }
        int fn = anomalyIdx.size()-tp; double precision = tp==0?0: (double)tp/(tp+fp); double recall = (double)tp/(tp+fn);
        assertTrue(precision >= 0.5, "Precision too low: "+precision);
        assertTrue(recall >= 0.5, "Recall too low: "+recall);
    }

    @Test
    void backtest_metricsPresent(){
        TimeSeriesAnalyticsService svc = spyService();
        int trainDays=60, testDays=14; int total = trainDays+testDays;
        List<TimeSeriesAnalyticsService.Point> pts = buildSeries(total, i-> 0.15 + i*0.001); // gentle upward trend
        doReturn(wrap(pts)).when(svc).daily(9L,total);
        Map<String,Object> res = svc.backtest(9L, trainDays, testDays);
        assertEquals("OK", res.get("status"));
        assertTrue(res.containsKey("MAE"));
        assertTrue(res.containsKey("MSE"));
        @SuppressWarnings("unchecked") List<Map<String,Object>> detail = (List<Map<String,Object>>) res.get("points");
        assertEquals(testDays, detail.size());
    }
}
