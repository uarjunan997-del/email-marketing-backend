package com.emailMarketing.deliverability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class UnsubscribeTokenService {
    @Value("${unsubscribe.secret:change-me-secret}")
    private String secret;

    private static final String HMAC_ALGO = "HmacSHA256";

    public String generate(Long userId, String email, Long campaignId){
        String payload = userId+"|"+email.toLowerCase()+"|"+campaignId;
        String sig = hmac(payload);
        return base64Url(payload)+"."+base64Url(sig);
    }

    public record Decoded(Long userId, String email, Long campaignId){}

    public Decoded validate(String token){
        if(token==null || !token.contains(".")) return null;
        String[] parts = token.split("\\.",2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String expectedSig = hmac(payload);
        String providedSig = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        if(!constantTimeEquals(expectedSig, providedSig)) return null;
        String[] fields = payload.split("\\|");
        if(fields.length!=3) return null;
        return new Decoded(Long.parseLong(fields[0]), fields[1], Long.parseLong(fields[2]));
    }

    private String hmac(String data){
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.getEncoder().encode(raw), StandardCharsets.UTF_8);
        } catch(Exception e){ throw new IllegalStateException("HMAC error", e); }
    }

    private String base64Url(String v){ return Base64.getUrlEncoder().withoutPadding().encodeToString(v.getBytes(StandardCharsets.UTF_8)); }

    private boolean constantTimeEquals(String a, String b){
        if(a.length()!=b.length()) return false; int r=0; for(int i=0;i<a.length();i++){ r |= a.charAt(i)^b.charAt(i);} return r==0;
    }
}
