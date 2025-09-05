package com.emailMarketing.deliverability;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import com.emailMarketing.contact.ContactRepository;
import com.emailMarketing.contact.Contact;

@RestController
@RequestMapping("/public/unsubscribe")
public class UnsubscribeController {
    private final ContactRepository contactRepository; private final SuppressionRepository suppressionRepository; private final UnsubscribeTokenService tokenService;
    public UnsubscribeController(ContactRepository contactRepository, SuppressionRepository suppressionRepository, UnsubscribeTokenService tokenService){ this.contactRepository=contactRepository; this.suppressionRepository=suppressionRepository; this.tokenService=tokenService; }

    @GetMapping
    @Transactional
    public ResponseEntity<String> unsubscribe(@RequestParam(value="token", required=false) String token, @RequestParam(value="u", required=false) Long userIdParam, @RequestParam(value="e", required=false) String emailParam){
    Long userId = userIdParam; String email = emailParam;
        if(token!=null){
            var decoded = tokenService.validate(token);
            if(decoded==null) return ResponseEntity.badRequest().body("Invalid token");
            userId = decoded.userId(); email = decoded.email();
        }
        if(userId==null || email==null) return ResponseEntity.badRequest().body("Missing parameters");
        final Long fUserId = userId; final String fEmail = email;
        var contacts = contactRepository.findByUserId(fUserId).stream().filter(c-> c.getEmail().equalsIgnoreCase(fEmail)).toList();
        for(Contact c: contacts){ c.setUnsubscribed(true); }
        if(!suppressionRepository.existsByUserIdAndEmail(fUserId, fEmail)){
            SuppressionEntry s = new SuppressionEntry(); s.setUserId(fUserId); s.setEmail(fEmail); s.setReason("UNSUB"); suppressionRepository.save(s);
        }
        return ResponseEntity.ok("You have been unsubscribed.");
    }
}
