package com.emailMarketing.email;

import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class SecondaryEmailProvider implements EmailProvider {
    private boolean enabled = true; // could be config driven
    @Override
    public EmailSendResult send(String to, String subject, String html){
        if(!enabled){
            return new EmailSendResult(false, EmailSendResult.ResultType.CONFIG_ERROR, "Secondary disabled", null, name());
        }
        // Simulate always success fallback
        return new EmailSendResult(true, EmailSendResult.ResultType.SUCCESS, "Fallback queued", null, name());
    }
    @Override public String name(){ return "secondary"; }
}
