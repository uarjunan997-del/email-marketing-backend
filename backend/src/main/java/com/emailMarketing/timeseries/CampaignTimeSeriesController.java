package com.emailMarketing.timeseries;

import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/timeseries")
@Tag(name="Campaign Time Series", description="Campaign-level time series analytics & alerts")
public class CampaignTimeSeriesController {
    private final CampaignTimeSeriesService service;
    public CampaignTimeSeriesController(CampaignTimeSeriesService s){this.service=s;}


    @GetMapping("/campaign/{id}/metrics")
    @Operation(summary="Campaign metrics series", description="Return aggregated daily time series for a campaign")
    public Object campaignSeries(@PathVariable Long id, @RequestParam(defaultValue="30") int days){ return service.campaignSeries(id, days); }

    @GetMapping("/campaign/{id}/metrics/paged")
    @Operation(summary="Paged campaign series", description="Return paginated daily time series slice")
    public Object campaignSeriesPaged(@PathVariable Long id, @RequestParam(defaultValue="30") int days, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size){ return service.campaignSeriesPaged(id, days, page, size); }

    @GetMapping("/dashboard/overview")
    @Operation(summary="Dashboard overview", description="Aggregated totals across campaigns for period")
    public Object dashboard(@RequestParam(defaultValue="30") int days){ return service.dashboardOverview(days); }

    @GetMapping("/trends/analysis")
    @Operation(summary="Trend analysis", description="Moving averages, direction, forecast, decomposition")
    public Object trend(@RequestParam Long campaignId, @RequestParam(defaultValue="60") int days){ return service.trendAnalysis(campaignId, days); }

    @GetMapping("/trends/decomposition")
    @Operation(summary="Decomposition", description="Weekly trend + seasonal factors")
    public Object decomposition(@RequestParam Long campaignId, @RequestParam(defaultValue="60") int days){ return service.trendAnalysis(campaignId, days).get("decomposition"); }

    @GetMapping("/compare")
    @Operation(summary="Period comparison", description="Compare current vs prior window")
    public Object compare(@RequestParam Long campaignId, @RequestParam(defaultValue="30") int days){ return service.comparePeriods(campaignId, days); }

    @GetMapping("/seasonal")
    @Operation(summary="Seasonal pattern", description="Weekday seasonality factors")
    public Object seasonal(@RequestParam Long campaignId, @RequestParam(defaultValue="8") int weeks){ return service.seasonalPattern(campaignId, weeks); }

    @GetMapping("/alerts")
    @Operation(summary="Performance alerts", description="Alert rules on open, click, conversion metrics")
    public Object alerts(@RequestParam Long campaignId, @RequestParam(defaultValue="30") int days, @RequestParam(defaultValue="0.15") double openRateThreshold){ return service.performanceAlerts(campaignId, days, openRateThreshold); }

    @GetMapping("/anomalies")
    @Operation(summary="Anomalies", description="MAD-based robust anomaly detection on open rate")
    public Object anomalies(@RequestParam Long campaignId, @RequestParam(defaultValue="60") int days){ return service.anomalies(campaignId, days); }
}
