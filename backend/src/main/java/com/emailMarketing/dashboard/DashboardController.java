package com.emailMarketing.dashboard;

import org.springframework.web.bind.annotation.*;

import com.emailMarketing.dashboard.dto.DashboardDtos.*;
import com.emailMarketing.subscription.UserRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service; private final UserRepository userRepository;
    public DashboardController(DashboardService service, UserRepository userRepository){this.service=service; this.userRepository=userRepository;}

    private Long uid(UserDetails p){ return userRepository.findByUsername(p.getUsername()).map(u->u.getId()).orElseThrow(); }

    @GetMapping("/overview") public Overview overview(@AuthenticationPrincipal UserDetails p){ return service.overview(uid(p)); }
    @GetMapping("/campaigns/recent") public java.util.List<RecentCampaign> recent(@AuthenticationPrincipal UserDetails p){ return service.recentCampaigns(uid(p)); }
    @GetMapping("/analytics/trends") public java.util.List<TrendsPoint> trends(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="30d") String period){ return service.trends(uid(p), period); }
    @GetMapping("/contacts/summary") public Overview contacts(@AuthenticationPrincipal UserDetails p){ return service.overview(uid(p)); }
    @GetMapping("/performance/top-campaigns") public java.util.List<TopCampaign> top(@AuthenticationPrincipal UserDetails p){ return service.topCampaigns(uid(p)); }
    @GetMapping("/deliverability/score") public java.util.Map<String,Object> deliverability(@AuthenticationPrincipal UserDetails p){ var o = service.overview(uid(p)); return java.util.Map.of("reputationScore", o.reputationScore()); }
    @GetMapping("/usage/limits") public Usage usage(@AuthenticationPrincipal UserDetails p){ return service.usage(uid(p)); }

    @PostMapping("/admin/evict")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public java.util.Map<String,String> evict(@AuthenticationPrincipal UserDetails p){
        Long id = uid(p);
        service.evictUserCaches(id);
        return java.util.Map.of("status","evicted");
    }
}
