package com.emailMarketing.email;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

@Service
public class OracleEmailDeliveryService implements EmailProvider {
    @Value("${oracle.email.delivery.endpoint:https://email.us.oraclecloud.com/20170907/actions/send}")
    private String endpoint;
    @Value("${oracle.email.delivery.compartment.ocid:}")
    private String compartmentOcid;
    @Value("${oracle.email.delivery.sender:}")
    private String defaultSender;
    @Value("${oracle.email.delivery.api.key:}")
    private String apiKey; // Placeholder - real auth uses OCI request signing; integrate SDK later

    public EmailSendResult sendDetailed(String to, String subject, String html){
        if(isBlank(defaultSender) || isBlank(compartmentOcid)){
            return new EmailSendResult(false, EmailSendResult.ResultType.CONFIG_ERROR, "Missing required Oracle Email Delivery configuration", null, name());
        }
        try {
            if(isBlank(apiKey)){
                return new EmailSendResult(false, EmailSendResult.ResultType.CONFIG_ERROR, "API key not configured", null, name());
            }
            // Simulated success; replace with OCI SDK action
            return new EmailSendResult(true, EmailSendResult.ResultType.SUCCESS, "Queued", null, name());
        } catch(Exception ex){
            String msg = ex.getMessage()==null?ex.toString():ex.getMessage();
            if(msg.contains("401") || msg.contains("403")) return new EmailSendResult(false, EmailSendResult.ResultType.CONFIG_ERROR, msg, null, name());
            if(msg.matches(".*4\\d{2}.*")) return new EmailSendResult(false, EmailSendResult.ResultType.PERMANENT_FAILURE, msg, null, name());
            return new EmailSendResult(false, EmailSendResult.ResultType.TRANSIENT_FAILURE, msg, Duration.ofSeconds(30), name());
        }
    }

    @Override
    public EmailSendResult send(String to, String subject, String html){ return sendDetailed(to, subject, html); }

    @Override
    public String name(){ return "oracle"; }

    private boolean isBlank(String v){ return v==null || v.isBlank(); }
}
