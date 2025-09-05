package com.emailMarketing.attribution;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.emailMarketing.subscription.UserRepository;
import com.emailMarketing.analytics.AttributionService;
import com.emailMarketing.attribution.repo.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AttributionController {
    private final UserRepository userRepo; private final AttributionService attributionService; private final AttributionModelRepository modelRepo; private final RevenueAttributionRepository revenueRepo; private final EmailInteractionRepository interactionRepo;
    public AttributionController(UserRepository userRepo, AttributionService attributionService, AttributionModelRepository modelRepo, RevenueAttributionRepository revenueRepo, EmailInteractionRepository interactionRepo){ this.userRepo=userRepo; this.attributionService=attributionService; this.modelRepo=modelRepo; this.revenueRepo=revenueRepo; this.interactionRepo=interactionRepo; }

    record ShopifyOrderPayload(String id, String email, Double total_price, String currency, Map<String,Object> raw){}
    @PostMapping("/webhooks/shopify/orders/paid")
    public ResponseEntity<?> shopifyOrdersPaid(@RequestBody Map<String,Object> body, @RequestHeader(value="X-Shopify-Topic", required=false) String topic, @RequestHeader(value="X-Shopify-Signature", required=false) String sig){
        // TODO: verify signature (HMAC) - placeholder
        String id = String.valueOf(body.getOrDefault("id", body.getOrDefault("order_id","unknown")));
        String email = (String)body.get("email");
        Double total = parseDouble(body.get("total_price"));
        String currency = (String)body.getOrDefault("currency","USD");
        Long userId = resolveMerchantUser(body); // Placeholder mapping
        var res = attributionService.ingestOrder(userId, id, email, currency, total==null?0.0:total, body);
        return ResponseEntity.ok(Map.of("status","OK","orderId", res.orderId(), "attributedCampaigns", res.attributedCampaigns()));
    }

    @PostMapping("/webhooks/woocommerce/order/completed")
    public ResponseEntity<?> wooOrderCompleted(@RequestBody Map<String,Object> body, @RequestHeader(value="X-WC-Webhook-Source", required=false) String source){
        String id = String.valueOf(body.getOrDefault("id", body.getOrDefault("order_id","unknown")));
    @SuppressWarnings("unchecked") Map<String,Object> billing = (Map<String,Object>)body.getOrDefault("billing", Collections.emptyMap());
        String email = (String)billing.get("email");
        Double total = parseDouble(body.get("total"));
        String currency = (String)body.getOrDefault("currency","USD");
        Long userId = resolveMerchantUser(body);
        var res = attributionService.ingestOrder(userId, id, email, currency, total==null?0:total, body);
        return ResponseEntity.ok(Map.of("status","OK","orderId",res.orderId()));
    }

    record RevenueBreakdown(Double totalRevenue, Integer orders, java.util.List<Map<String,Object>> modelBreakdown){}
    @GetMapping("/attribution/campaign/{id}/revenue")
    public RevenueBreakdown campaignRevenue(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id){
        // Query revenue across models from repository (simplified: use revenue_attribution direct)
        var all = revenueRepo.findByCampaignIdAndModelCode(id, "LAST_TOUCH"); // extend to other models with loops
        double total = all.stream().mapToDouble(r->r.getAttributedAmount()==null?0:r.getAttributedAmount()).sum();
        int orders = (int)all.stream().map(r->r.getOrderId()).distinct().count();
        java.util.List<Map<String,Object>> breakdown = new java.util.ArrayList<>();
        breakdown.add(Map.of("model","LAST_TOUCH","revenue", total, "orders", orders));
        return new RevenueBreakdown(total, orders, breakdown);
    }

    record AttributionModelDto(String code, String description, Double decayHalfLifeDays){}
    @GetMapping("/attribution/models")
    public java.util.List<AttributionModelDto> models(){
        return modelRepo.findAll().stream().map(m-> new AttributionModelDto(m.getModelCode(), m.getDescription(), m.getDecayHalfLifeDays())).toList();
    }

    record CustomEventRequest(String email, Long campaignId, String type, String variant, String utmSource, String utmMedium, String utmCampaign){ }
    @PostMapping("/attribution/custom-event")
    public ResponseEntity<?> customEvent(@AuthenticationPrincipal UserDetails principal, @RequestBody CustomEventRequest req){
        Long userId = resolveUserId(principal);
        EmailInteraction ei = new EmailInteraction();
        ei.setUserId(userId); ei.setCampaignId(req.campaignId()); ei.setEmail(req.email()); ei.setEventType(req.type()); ei.setEventTime(LocalDateTime.now()); ei.setVariantCode(req.variant()); ei.setUtmSource(req.utmSource()); ei.setUtmMedium(req.utmMedium()); ei.setUtmCampaign(req.utmCampaign()); ei.setChannel("EMAIL");
        interactionRepo.save(ei);
        return ResponseEntity.ok(Map.of("status","OK"));
    }

    private Double parseDouble(Object o){ try { return o==null?null: Double.valueOf(String.valueOf(o)); } catch(Exception ex){ return null; } }
    private Long resolveMerchantUser(Map<String,Object> payload){ return 1L; } // TODO: map store domain/api key to user id
    private Long resolveUserId(UserDetails principal){ return userRepo.findByUsername(principal.getUsername()).map(u->u.getId()).orElseThrow(); }
}
