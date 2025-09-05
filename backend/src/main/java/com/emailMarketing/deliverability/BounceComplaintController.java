package com.emailMarketing.deliverability;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/public/deliverability")
public class BounceComplaintController {
    private final EmailBounceRepository bounceRepo; private final EmailComplaintRepository complaintRepo; private final SuppressionRepository suppressionRepo;
    public BounceComplaintController(EmailBounceRepository bounceRepo, EmailComplaintRepository complaintRepo, SuppressionRepository suppressionRepo){ this.bounceRepo=bounceRepo; this.complaintRepo=complaintRepo; this.suppressionRepo=suppressionRepo; }

    public record BounceRequest(Long userId, Long campaignId, String email, String type, String reason){}
    public record ComplaintRequest(Long userId, Long campaignId, String email, String source){}

    @PostMapping("/bounce")
    @Transactional
    public ResponseEntity<?> bounce(@RequestBody BounceRequest req){
        if(req.email()==null || req.userId()==null) return ResponseEntity.badRequest().body("Missing email or userId");
        if(!bounceRepo.existsByUserIdAndEmail(req.userId(), req.email())){
            EmailBounce b = new EmailBounce(); b.setUserId(req.userId()); b.setCampaignId(req.campaignId()); b.setEmail(req.email()); b.setBounceType(req.type()); b.setReason(req.reason()); bounceRepo.save(b);
            // add to suppression
            if(!suppressionRepo.existsByUserIdAndEmail(req.userId(), req.email())){
                SuppressionEntry s = new SuppressionEntry(); s.setUserId(req.userId()); s.setEmail(req.email()); s.setReason("BOUNCE"); suppressionRepo.save(s);
            }
        }
        return ResponseEntity.ok().body("OK");
    }

    @PostMapping("/complaint")
    @Transactional
    public ResponseEntity<?> complaint(@RequestBody ComplaintRequest req){
        if(req.email()==null || req.userId()==null) return ResponseEntity.badRequest().body("Missing email or userId");
        if(!complaintRepo.existsByUserIdAndEmail(req.userId(), req.email())){
            EmailComplaint c = new EmailComplaint(); c.setUserId(req.userId()); c.setCampaignId(req.campaignId()); c.setEmail(req.email()); c.setSource(req.source()); complaintRepo.save(c);
            if(!suppressionRepo.existsByUserIdAndEmail(req.userId(), req.email())){
                SuppressionEntry s = new SuppressionEntry(); s.setUserId(req.userId()); s.setEmail(req.email()); s.setReason("COMPLAINT"); suppressionRepo.save(s);
            }
        }
        return ResponseEntity.ok().body("OK");
    }
}
