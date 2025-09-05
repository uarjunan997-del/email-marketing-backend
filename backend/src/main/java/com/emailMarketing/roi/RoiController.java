package com.emailMarketing.roi;

import org.springframework.web.bind.annotation.*; import java.util.*; import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag; import io.swagger.v3.oas.annotations.Operation;

@RestController @RequestMapping("/api") @Tag(name="ROI")
public class RoiController {
    private final RoiService service;
    public RoiController(RoiService s){ this.service=s; }

    @PostMapping("/costs/campaign/{id}") @Operation(summary="Add campaign costs")
    public ResponseEntity<?> addCosts(@PathVariable Long id, @jakarta.validation.Valid @RequestBody List<RoiService.AddCostRequest> req){ return new ResponseEntity<>(service.addCampaignCosts(id, req), HttpStatus.CREATED); }

    @GetMapping("/costs/categories") @Operation(summary="List cost categories")
    public Map<String,Object> categories(){ return Map.of("status","OK","categories", service.categories()); }

    @GetMapping("/roi/campaign/{id}") @Operation(summary="Campaign ROI analysis")
    public Map<String,Object> campaignRoi(@PathVariable Long id, @RequestParam(defaultValue="30") int days){ return service.campaignRoi(id, days); }

    @GetMapping("/roi/dashboard") @Operation(summary="ROI dashboard overview")
    public Map<String,Object> dashboard(@RequestParam(defaultValue="30") int days){ return service.roiDashboard(days); }

    @GetMapping("/roi/trends") @Operation(summary="ROI trends over time")
    public Map<String,Object> trends(@RequestParam(defaultValue="90") int days){ return service.roiTrends(days); }

    @PostMapping("/roi/goals") @Operation(summary="Set ROI goals/targets")
    public Map<String,Object> setGoals(@RequestBody List<RoiService.RoiGoalRequest> req){ return service.setRoiGoals(req); }

    @GetMapping("/roi/channel") @Operation(summary="Channel-level ROI overview")
    public Map<String,Object> channel(@RequestParam Long orgId, @RequestParam(defaultValue="30") int days){ return service.channelRoi(orgId, days); }

    @GetMapping("/roi/ltv/{campaignId}") @Operation(summary="LTV attribution for a campaign")
    public Map<String,Object> ltv(@PathVariable Long campaignId, @RequestParam(defaultValue="90") int days){ return service.ltvAttribution(campaignId, days); }

    @PostMapping("/roi/allocation/apportion") @Operation(summary="Apportion shared cost across campaigns")
    public Map<String,Object> apportion(@RequestParam double totalCost, @RequestBody Map<Long,Double> weights){ return service.apportionAllocation(totalCost, weights); }

    @GetMapping("/roi/forecast") @Operation(summary="Forecast org ROI")
    public Map<String,Object> forecast(@RequestParam Long orgId, @RequestParam(defaultValue="90") int days, @RequestParam(defaultValue="30") int horizon){ return service.forecastOrgRoi(orgId, days, horizon); }

    @GetMapping("/roi/benchmarks") @Operation(summary="Industry ROI benchmarks")
    public Map<String,Object> benchmarks(@RequestParam(required=false) String industry){ return service.benchmarks(industry); }

    @PostMapping("/roi/snapshot/{campaignId}") @Operation(summary="Persist ROI analysis snapshot for campaign")
    public Map<String,Object> snapshot(@PathVariable Long campaignId){ return service.snapshotCampaignRoi(campaignId); }
}
