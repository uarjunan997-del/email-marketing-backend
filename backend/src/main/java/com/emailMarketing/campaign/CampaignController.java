package com.emailMarketing.campaign;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.emailMarketing.subscription.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/campaigns")
public class CampaignController {
    private final CampaignService service; private final UserRepository userRepository;
    public CampaignController(CampaignService service, UserRepository userRepository){this.service=service; this.userRepository=userRepository;}

    public record CampaignRequest(String name, String segment, Long templateId, LocalDateTime scheduledAt){}

    @GetMapping
    public List<Campaign> list(@AuthenticationPrincipal UserDetails principal){ return service.list(resolveUserId(principal)); }

    @PostMapping
    public Campaign create(@AuthenticationPrincipal UserDetails principal, @RequestBody CampaignRequest req){
        Campaign c = new Campaign();
        c.setUserId(resolveUserId(principal));
        c.setName(req.name()); c.setSegment(req.segment()); c.setTemplateId(req.templateId()); c.setScheduledAt(req.scheduledAt());
        if(req.scheduledAt()!=null){ c.setStatus("SCHEDULED"); }
        return service.create(c);
    }

    private Long resolveUserId(UserDetails principal){
        return userRepository.findByUsername(principal.getUsername()).map(u->u.getId()).orElseThrow();
    }
}
