package com.emailMarketing.publicapi;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.emailMarketing.deliverability.UnsubscribeTokenService;
import com.emailMarketing.deliverability.SuppressionRepository;
import com.emailMarketing.deliverability.SuppressionEntry;
import com.emailMarketing.deliverability.UnsubscribeEvent;
import com.emailMarketing.deliverability.UnsubscribeEventRepository;
import com.emailMarketing.contact.ContactRepository;
import com.emailMarketing.contact.Contact;

import java.util.Map;

/**
 * Public unsubscribe endpoints (no auth). Accepts a signed token referencing userId, email, campaignId.
 * Flow:
 *  GET /public/unsubscribe/verify?token=... -> { valid: true, email, campaignId } or 400 if invalid
 *  POST /public/unsubscribe/confirm { token, reason? } -> marks contact unsubscribed + adds suppression entry.
 */
@RestController
@RequestMapping("/public/unsubscribe")
public class PublicUnsubscribeController {
    private final UnsubscribeTokenService tokenService;
    private final ContactRepository contactRepository;
    private final SuppressionRepository suppressionRepository;
    private final UnsubscribeEventRepository unsubscribeEventRepository;

    public PublicUnsubscribeController(UnsubscribeTokenService tokenService, ContactRepository contactRepository, SuppressionRepository suppressionRepository, UnsubscribeEventRepository unsubscribeEventRepository) {
        this.tokenService = tokenService;
        this.contactRepository = contactRepository;
        this.suppressionRepository = suppressionRepository;
        this.unsubscribeEventRepository = unsubscribeEventRepository;
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token){
        var decoded = tokenService.validate(token);
        if(decoded == null){
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "Invalid token"));
        }
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "email", decoded.email(),
                "campaignId", decoded.campaignId(),
                "userId", decoded.userId()));
    }

    public record UnsubRequest(String token, String reason){}

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody UnsubRequest req){
        if(req == null || req.token() == null) return ResponseEntity.badRequest().body(Map.of("error","Missing token"));
        var decoded = tokenService.validate(req.token());
        if(decoded == null){
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid token"));
        }
        // Find contact by user + email
        Contact contact = contactRepository.findAll().stream()
                .filter(c -> decoded.userId().equals(c.getUserId()) && c.getEmail().equalsIgnoreCase(decoded.email()))
                .findFirst().orElse(null);
        if(contact != null){
            contact.setUnsubscribed(true);
            contactRepository.save(contact);
        }
        // Add suppression entry if not present
        if(!suppressionRepository.existsByUserIdAndEmail(decoded.userId(), decoded.email())){
            SuppressionEntry se = new SuppressionEntry();
            se.setUserId(decoded.userId());
            se.setEmail(decoded.email());
            se.setReason(req.reason() != null ? req.reason() : "UNSUBSCRIBE_LINK");
            suppressionRepository.save(se);
        }
    // Log unsubscribe event
    UnsubscribeEvent evt = new UnsubscribeEvent();
    evt.setUserId(decoded.userId());
    evt.setCampaignId(decoded.campaignId());
    evt.setEmail(decoded.email());
    evt.setReason(req.reason() != null ? req.reason() : "UNSUBSCRIBE_LINK");
    unsubscribeEventRepository.save(evt);
    return ResponseEntity.ok(Map.of(
        "status","UNSUBSCRIBED",
        "email", decoded.email(),
        "campaignId", decoded.campaignId()));
    }
}
