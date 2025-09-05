package com.emailMarketing.webhook;

import org.springframework.web.bind.annotation.*; import org.springframework.http.ResponseEntity; import org.springframework.http.HttpStatus; import org.springframework.jdbc.core.JdbcTemplate; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.Base64; import org.springframework.beans.factory.annotation.Value; import org.springframework.kafka.core.KafkaTemplate; import com.emailMarketing.streaming.KafkaTopics; import java.time.Instant;

@RestController
@RequestMapping("/webhook/ecommerce")
public class EcommerceWebhookController {
    private static final Logger log = LoggerFactory.getLogger(EcommerceWebhookController.class);
    private final JdbcTemplate jdbc; private final KafkaTemplate<String,Object> kafka; private final String shopifySecret;
    public EcommerceWebhookController(JdbcTemplate jdbc, KafkaTemplate<String,Object> kafka, @Value("${shopify.secret:changeme}") String shopifySecret){ this.jdbc=jdbc; this.kafka=kafka; this.shopifySecret=shopifySecret; }

    @PostMapping("/shopify")
    public ResponseEntity<String> shopify(@RequestBody String body, @RequestHeader(value="X-Shopify-Hmac-Sha256", required=false) String sig){
        boolean valid = validateHmac(body, sig, shopifySecret); String status = valid?"NEW":"INVALID_SIG"; try{
            jdbc.update("INSERT INTO ECOM_WEBHOOK_RAW(PROVIDER,EXT_ID,PAYLOAD,SIG_VALID,STATUS) VALUES(?,?,?,?,?)","SHOPIFY", extractOrderId(body), body, valid?"Y":"N", status);
            if(valid){ kafka.send(KafkaTopics.ECOMMERCE_ORDERS, extractOrderId(body), body); }
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("ok");
        }catch(Exception ex){ log.error("shopify_webhook_error msg={}", ex.getMessage()); return ResponseEntity.internalServerError().body("err"); }
    }

    private boolean validateHmac(String body, String sig, String secret){ if(sig==null) return false; try{ javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256"); mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); String calc = Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))); return MessageDigest.isEqual(calc.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8)); }catch(Exception e){ return false; } }
    // Simplistic extractor placeholder
    private String extractOrderId(String body){ int i = body.indexOf("order_id"); if(i<0) return "unknown-"+ Instant.now().toEpochMilli(); return "ord-"+ Integer.toHexString(body.hashCode()); }
}
