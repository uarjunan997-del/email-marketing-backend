package com.emailMarketing.campaign;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.emailMarketing.subscription.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
    private final CampaignService service;
    private final UserRepository userRepository;

    public CampaignController(CampaignService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    public record CampaignRequest(String name, String segment, Long templateId, String subject, String preheader,
            LocalDateTime scheduledAt) {
    }

    public record ScheduleRequest(LocalDateTime scheduledAt, String timezone, LocalDateTime windowStart,
            LocalDateTime windowEnd, String optimizationStrategy) {
    }

    public record ABTestRequest(List<Variant> variants, Integer samplePercent) {
        public record Variant(String code, String subject, Long templateId, Integer splitPercent) {
        }
    }

    public record ProgressResponse(String status, int total, int sent, int opens, int clicks) {
    }

    public record GenericResponse(String status) {
    }

    public record AnalyticsResponse(String status, int sent, int opens, int clicks, Double revenue, Integer orders,
            Double ctr, Double openRate, Double roi) {
    }

    public record SummaryAnalyticsResponse(int campaigns, int totalSent, int totalOpens, int totalClicks,
            double avgOpenRate, double avgCtr, Double totalRevenue, Double avgRoi) {
    }

    @GetMapping
    public List<Campaign> list(@AuthenticationPrincipal UserDetails principal) {
        return service.list(resolveUserId(principal));
    }

    @PostMapping
    public Campaign create(@AuthenticationPrincipal UserDetails principal, @RequestBody CampaignRequest req) {
        Campaign c = new Campaign();
        c.setUserId(resolveUserId(principal));
        c.setName(req.name());
        c.setSegment(req.segment());
        c.setTemplateId(req.templateId());
        c.setSubject(req.subject());
        c.setPreheader(req.preheader());
        if (req.scheduledAt() != null) {
            c.setScheduledAt(req.scheduledAt());
            c.setStatus("SCHEDULED");
        }
        return service.create(c);
    }

    @GetMapping("/{id}")
    public Campaign get(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return service.getForUser(resolveUserId(principal), id);
    }

    @PutMapping("/{id}")
    public Campaign update(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestBody CampaignRequest req) {
        return service.update(resolveUserId(principal), id, req);
    }

    @PostMapping("/{id}/schedule")
    public Campaign schedule(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestBody ScheduleRequest req) {
        return service.schedule(resolveUserId(principal), id, req);
    }

    @PostMapping("/{id}/send-now")
    public Campaign sendNow(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return service.sendNow(resolveUserId(principal), id);
    }

    @PostMapping("/{id}/cancel")
    public Campaign cancel(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return service.cancel(resolveUserId(principal), id);
    }

    @GetMapping("/{id}/preview")
    public java.util.Map<String, Object> preview(@AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id) {
        return service.preview(resolveUserId(principal), id);
    }

    @PostMapping("/{id}/ab-test")
    public java.util.Map<String, String> abTest(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestBody ABTestRequest req) {
        service.setupAbTest(resolveUserId(principal), id, req);
        return java.util.Map.of("status", "OK");
    }

    @GetMapping("/{id}/progress")
    public ProgressResponse progress(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        var p = service.progress(resolveUserId(principal), id);
        return new ProgressResponse(p.status(), p.total(), p.sent(), p.opens(), p.clicks());
    }

    @GetMapping("/{id}/analytics")
    public AnalyticsResponse analytics(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        var a = service.analytics(resolveUserId(principal), id);
        return new AnalyticsResponse(a.status(), a.sent(), a.opens(), a.clicks(), a.revenue(), a.orders(), a.ctr(),
                a.openRate(), a.roi());
    }

    @PostMapping("/{id}/analyze")
    public GenericResponse analyze(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        service.runAnalysis(resolveUserId(principal), id);
        return new GenericResponse("OK");
    }

    @GetMapping("/summary/analytics")
    public SummaryAnalyticsResponse summary(@AuthenticationPrincipal UserDetails principal) {
        var s = service.analyticsSummary(resolveUserId(principal));
        return new SummaryAnalyticsResponse(s.campaigns(), s.totalSent(), s.totalOpens(), s.totalClicks(),
                s.avgOpenRate(), s.avgCtr(), s.totalRevenue(), s.avgRoi());
    }

    // Approval workflow
    @PostMapping("/{id}/request-approval")
    public Campaign requestApproval(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return service.requestApproval(resolveUserId(principal), id);
    }

    @PostMapping("/{id}/approve")
    public Campaign approve(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return service.approve(resolveUserId(principal), id);
    }

    @PostMapping("/{id}/reject")
    public Campaign reject(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String notes = body == null ? null : body.get("notes");
        return service.reject(resolveUserId(principal), id, notes);
    }

    private Long resolveUserId(UserDetails principal) {
        return userRepository.findByUsername(principal.getUsername()).map(u -> u.getId()).orElseThrow();
    }
}
