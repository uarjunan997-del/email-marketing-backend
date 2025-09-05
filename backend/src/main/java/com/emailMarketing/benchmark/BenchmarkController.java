package com.emailMarketing.benchmark;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.ResponseEntity;
import com.emailMarketing.subscription.UserRepository;
import java.util.*;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {
    private final BenchmarkService service; private final UserRepository userRepo;
    public BenchmarkController(BenchmarkService service, UserRepository userRepo){ this.service=service; this.userRepo=userRepo; }

    @GetMapping("/industries")
    public List<String> industries(){ return service.industries(); }

    @GetMapping("/compare/{industry}/{listSize}")
    public BenchmarkService.ComparisonResult compare(@AuthenticationPrincipal UserDetails principal, @PathVariable String industry, @PathVariable int listSize, @RequestParam(defaultValue="NA") String region){
        Long uid = resolveUserId(principal);
        String tier = service.deriveListTier(listSize);
        return service.compare(uid, industry.toUpperCase(), tier, region.toUpperCase());
    }

    // Slimmed dashboard oriented view for quick widget load
    @GetMapping("/compare/{industry}/{listSize}/dashboard")
    public Map<String,Object> compareDashboard(@AuthenticationPrincipal UserDetails principal, @PathVariable String industry, @PathVariable int listSize, @RequestParam(defaultValue="NA") String region){
        Long uid = resolveUserId(principal);
        String tier = service.deriveListTier(listSize);
        var r = service.compare(uid, industry.toUpperCase(), tier, region.toUpperCase());
    Map<String,Object> out = new HashMap<>();
    out.put("industry", r.industry());
    out.put("tier", r.listTier());
    out.put("region", r.region());
    out.put("openRate", Map.of("user", r.openRateUser(), "median", r.openRateMedian(), "pct", r.openRatePercentile()));
    out.put("clickRate", Map.of("user", r.clickRateUser(), "median", r.clickRateMedian(), "pct", r.clickRatePercentile()));
    out.put("conversionRate", Map.of("user", r.conversionRateUser(), "median", r.conversionRateMedian(), "pct", r.conversionRatePercentile()));
    out.put("revenuePerEmail", Map.of("user", r.revenuePerEmailUser(), "median", r.revenuePerEmailMedian(), "pct", r.revenuePerEmailPercentile()));
    out.put("bounceRate", Map.of("user", r.bounceRateUser(), "median", r.bounceRateMedian(), "pct", r.bounceRatePercentile()));
    out.put("composite", r.composite());
    out.put("radar", r.chart().get("radar"));
    out.put("performance", r.performanceScore());
    out.put("recommendations", r.recommendations());
    return out;
    }

    record ClassifyRequest(String industry, Integer contacts){ }
    @PostMapping("/classify")
    public ResponseEntity<?> classify(@AuthenticationPrincipal UserDetails principal, @RequestBody ClassifyRequest req){
        Long uid = resolveUserId(principal);
        var c = service.classify(uid, req.industry(), req.contacts()==null?0:req.contacts());
        return ResponseEntity.ok(Map.of("industry", c.getIndustry(), "confidence", c.getConfidence()));
    }

    @GetMapping("/recommendations")
    public List<Map<String,Object>> recommendations(@AuthenticationPrincipal UserDetails principal){
        Long uid = resolveUserId(principal);
        var comps = service.recentComparisons(uid);
        List<Map<String,Object>> out = new ArrayList<>();
        for(var c: comps){
            out.add(Map.of(
                "computedAt", c.getComputedAt(),
                "industry", c.getIndustry(),
                "performance", c.getPerformanceScore(),
                "openRatePercentile", c.getOpenRatePercentile(),
                "recommendations", c.getRecommendations()==null?List.of(): List.of(c.getRecommendations().split("\\n"))
            ));
        }
        return out;
    }

    private Long resolveUserId(UserDetails principal){ return userRepo.findByUsername(principal.getUsername()).map(u->u.getId()).orElseThrow(); }
}
