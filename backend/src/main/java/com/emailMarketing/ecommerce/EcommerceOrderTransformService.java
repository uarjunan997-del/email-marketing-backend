package com.emailMarketing.ecommerce;

import org.springframework.stereotype.Service; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.transaction.annotation.Transactional; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.kafka.core.KafkaTemplate; import com.emailMarketing.streaming.KafkaTopics; import java.time.*; import java.sql.*; import java.util.*;

@Service
public class EcommerceOrderTransformService {
    private static final Logger log = LoggerFactory.getLogger(EcommerceOrderTransformService.class);
    private final JdbcTemplate jdbc; private final KafkaTemplate<String,Object> kafka;
    public EcommerceOrderTransformService(JdbcTemplate jdbc, KafkaTemplate<String,Object> kafka){ this.jdbc=jdbc; this.kafka=kafka; }

    // Simple transform: parse minimal fields from JSON payload (provider-specific logic can be extended)
    @Transactional
    public int processNewRaw(int max){
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT ID, PROVIDER, EXT_ID, PAYLOAD FROM ECOM_WEBHOOK_RAW WHERE STATUS='NEW' AND ROWNUM<=?", max);
        int processed=0;
        for(var r: rows){
            Long id = ((Number)r.get("ID")).longValue(); String provider=(String)r.get("PROVIDER"); String extId=(String)r.get("EXT_ID"); String payload=(String)r.get("PAYLOAD");
            try {
                Map<String,Object> parsed = quickParse(payload);
                String orderId = (String)parsed.getOrDefault("order_id", extId!=null? extId: UUID.randomUUID().toString());
                Timestamp orderTs = Timestamp.from((Instant)parsed.getOrDefault("order_ts", Instant.now()));
                Double gross = (Double)parsed.getOrDefault("gross", 0.0);
                String currency = (String)parsed.getOrDefault("currency", "USD");
                Long userId = (Long)parsed.getOrDefault("user_id", 0L);
                Long campaignId = (Long)parsed.getOrDefault("campaign_id", 0L);
                // Upsert into ORDER_ATTRIBUTION (ignore duplicates)
                jdbc.update("MERGE INTO ORDER_ATTRIBUTION tgt USING (SELECT ? ORDER_ID FROM dual) src ON (tgt.ORDER_ID=src.ORDER_ID) WHEN NOT MATCHED THEN INSERT (ORDER_ID,USER_ID,CAMPAIGN_ID,ORDER_TS,CURRENCY,GROSS_AMOUNT,NET_AMOUNT,ATTR_METHOD,SOURCE_CHANNEL) VALUES (?,?,?,?,?,?,?, ?, ?)",
                        orderId, orderId, userId, campaignId, orderTs, currency, gross, gross, "LAST_TOUCH", provider);
                jdbc.update("UPDATE ECOM_WEBHOOK_RAW SET STATUS='PROCESSED' WHERE ID=?", id);
                // Emit cache invalidation for campaign stats & ROI if campaign present
                if(campaignId!=null && campaignId>0){ kafka.send(KafkaTopics.CACHE_INVALIDATE, "campaign:"+campaignId, Map.of("type","campaign","id",campaignId)); }
                processed++;
            }catch(Exception ex){
                log.error("order_transform_error rawId={} msg={}", id, ex.getMessage());
                jdbc.update("UPDATE ECOM_WEBHOOK_RAW SET STATUS='ERROR', ERROR_MSG=? WHERE ID=?", truncate(ex.getMessage()), id);
            }
        }
        return processed;
    }

    private String truncate(String m){ if(m==null) return null; return m.length()>3900? m.substring(0,3900): m; }

    // Very naive JSON key extraction (assumes flat JSON with double quotes). For production use a real JSON parser.
    private Map<String,Object> quickParse(String json){
        Map<String,Object> map=new HashMap<>(); if(json==null) return map; try {
            extractString(json, "id").ifPresent(v-> map.put("order_id", v));
            extractString(json, "order_id").ifPresent(v-> map.put("order_id", v));
            extractString(json, "currency").ifPresent(v-> map.put("currency", v));
            extractNumber(json, "total_price").ifPresent(v-> map.put("gross", v));
            extractNumber(json, "gross_amount").ifPresent(v-> map.put("gross", v));
            extractNumber(json, "campaign_id").ifPresent(v-> map.put("campaign_id", v.longValue()));
            extractNumber(json, "user_id").ifPresent(v-> map.put("user_id", v.longValue()));
            extractTimestamp(json, "created_at").ifPresent(v-> map.put("order_ts", v));
        }catch(Exception ignored){ }
        return map;
    }
    private Optional<String> extractString(String json, String key){ int i=json.indexOf("\""+key+"\""); if(i<0) return Optional.empty(); int c=json.indexOf(':', i); if(c<0) return Optional.empty(); int q1=json.indexOf('"', c+1); if(q1<0) return Optional.empty(); int q2=json.indexOf('"', q1+1); if(q2<0) return Optional.empty(); return Optional.of(json.substring(q1+1,q2)); }
    private Optional<Double> extractNumber(String json, String key){ int i=json.indexOf("\""+key+"\""); if(i<0) return Optional.empty(); int c=json.indexOf(':', i); if(c<0) return Optional.empty(); int e=c+1; while(e<json.length() && (Character.isWhitespace(json.charAt(e))||json.charAt(e)=='"')) e++; int s=e; while(e<json.length() && (Character.isDigit(json.charAt(e))||json.charAt(e)=='.')) e++; if(s==e) return Optional.empty(); try { return Optional.of(Double.parseDouble(json.substring(s,e))); }catch(Exception ex){ return Optional.empty(); } }
    private Optional<Instant> extractTimestamp(String json, String key){ return extractString(json,key).map(v-> { try { return Instant.parse(v); }catch(Exception ex){ return Instant.now(); }}); }
}