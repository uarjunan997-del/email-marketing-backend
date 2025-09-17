package com.emailMarketing.email;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Oracle Email Delivery implementation backed by Spring's JavaMailSender (SMTP over STARTTLS).
 * Configuration expected (example):
 *
 * spring.mail.host=smtp.email.us-ashburn-1.oci.oraclecloud.com
 * spring.mail.port=587
 * spring.mail.username=<smtp_credential_user_ocid_or_name>
 * spring.mail.password=<smtp_credential_password>
 * spring.mail.properties.mail.smtp.auth=true
 * spring.mail.properties.mail.smtp.starttls.enable=true
 * spring.mail.properties.mail.smtp.connectiontimeout=5000
 * spring.mail.properties.mail.smtp.timeout=5000
 * spring.mail.properties.mail.smtp.writetimeout=5000
 * oracle.email.delivery.sender=no-reply@yourverifieddomain.com
 * oracle.email.delivery.region=us-ashburn-1
 * (optionally) oracle.email.delivery.enabled=true
 */
@Service
@Order(0) // prefer this provider before generic fallbacks
public class OracleEmailDeliveryService implements EmailProvider {

    private final JavaMailSender mailSender;

    @Value("${oracle.email.delivery.sender:}")
    private String defaultSender;
    @Value("${oracle.email.delivery.enabled:true}")
    private boolean enabled;
    @Value("${oracle.email.delivery.region:}")
    private String region; // informational / logging

    public OracleEmailDeliveryService( JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public EmailSendResult sendDetailed(String to, String subject, String html) {
        if(!enabled) {
            return new EmailSendResult(false, EmailSendResult.ResultType.CONFIG_ERROR, "Oracle Email Delivery disabled", null, name());
        }
        if (isBlank(defaultSender)) {
            return new EmailSendResult(false, EmailSendResult.ResultType.CONFIG_ERROR, "oracle.email.delivery.sender not configured", null, name());
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(defaultSender);
            helper.setTo(to);
            helper.setSubject(subject == null ? "" : subject);
            helper.setText(html == null ? "" : html, true);
            // Instrumentation headers (optional, ignored by most MTAs but useful for logs)
            // Set via ThreadLocal or MDC before calling send if available
            try {
                String campaignId = CampaignContextHolder.getCampaignId();
                if(campaignId != null) {
                    message.setHeader("X-Campaign-Id", campaignId);
                }
                String userId = CampaignContextHolder.getUserId();
                if(userId != null) {
                    message.setHeader("X-User-Id", userId);
                }
                if(region != null && !region.isBlank()) {
                    message.setHeader("X-OCI-Region", region);
                }
            } catch (Throwable ignored) { /* non-fatal */ }
            mailSender.send(message);
            return new EmailSendResult(true, EmailSendResult.ResultType.SUCCESS, "Sent", null, name());
        } catch (SendFailedException sfe) {
            return classifyMessagingException(sfe);
        } catch (MessagingException me) {
            return classifyMessagingException(me);
        } catch (Exception ex) {
            String msg = safeMessage(ex);
            return new EmailSendResult(false, EmailSendResult.ResultType.TRANSIENT_FAILURE, msg, Duration.ofSeconds(30), name());
        }
    }

    private EmailSendResult classifyMessagingException(MessagingException me) {
        String msg = safeMessage(me);
        EmailSendResult.ResultType type = determineResultType(msg);
        Duration retry = type == EmailSendResult.ResultType.TRANSIENT_FAILURE ? Duration.ofSeconds(60) : null;
        return new EmailSendResult(false, type, msg, retry, name());
    }

    private EmailSendResult.ResultType determineResultType(String msg) {
        if (msg == null) return EmailSendResult.ResultType.TRANSIENT_FAILURE;
        String lower = msg.toLowerCase(Locale.ROOT);
        // Heuristics: 4xx codes transient, 5xx permanent, auth/config keywords => CONFIG_ERROR
        if (lower.contains("authentication failed") || lower.contains("invalid credentials") || lower.contains("not authorized")) {
            return EmailSendResult.ResultType.CONFIG_ERROR;
        }
        if (lower.matches(".*\n?4\\d{2}.*") || lower.matches(".* 4\\d{2} .*")) {
            return EmailSendResult.ResultType.TRANSIENT_FAILURE; // 4xx considered retryable here
        }
        if (lower.matches(".*\n?5\\d{2}.*") || lower.matches(".* 5\\d{2} .*")) {
            return EmailSendResult.ResultType.PERMANENT_FAILURE;
        }
        // Fallback: treat unknown as transient to allow failover
        return EmailSendResult.ResultType.TRANSIENT_FAILURE;
    }

    private String safeMessage(Exception ex) { return ex.getMessage() == null ? ex.toString() : ex.getMessage(); }

    @Override
    public EmailSendResult send(String to, String subject, String html) { return sendDetailed(to, subject, html); }

    @Override
    public String name() { return "oracle"; }

    private boolean isBlank(String v) { return v == null || v.isBlank(); }
}

// Simple context holder for campaign metadata; populate before invoking provider, e.g., in queue processor.
class CampaignContextHolder {
    private static final ThreadLocal<String> CAMPAIGN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    static void set(String campaignId, String userId){ CAMPAIGN_ID.set(campaignId); USER_ID.set(userId); }
    static void clear(){ CAMPAIGN_ID.remove(); USER_ID.remove(); }
    static String getCampaignId(){ return CAMPAIGN_ID.get(); }
    static String getUserId(){ return USER_ID.get(); }
}
