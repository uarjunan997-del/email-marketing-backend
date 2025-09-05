package com.emailMarketing.campaign;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.emailMarketing.subscription.UserRepository;
import java.io.IOException;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignProgressSseController {
    private final CampaignService service; private final UserRepository userRepository;
    public CampaignProgressSseController(CampaignService service, UserRepository userRepository){ this.service=service; this.userRepository=userRepository; }

    @GetMapping(value="/{id}/progress/stream", produces= MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id){
        Long uid = userRepository.findByUsername(principal.getUsername()).map(u->u.getId()).orElseThrow();
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        new Thread(() -> {
            try {
                while(true){
                    var p = service.progress(uid, id);
                    emitter.send(SseEmitter.event().name("progress").data(p));
                    if("SENT".equals(p.status()) || "FAILED".equals(p.status()) || "CANCELLED".equals(p.status())){ break; }
                    Thread.sleep(3000L);
                }
                emitter.complete();
            } catch (IOException | InterruptedException e){
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }
}
