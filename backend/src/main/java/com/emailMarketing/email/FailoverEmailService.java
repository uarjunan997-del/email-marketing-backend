package com.emailMarketing.email;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FailoverEmailService {
    private final List<EmailProvider> providers;
    public FailoverEmailService(List<EmailProvider> providers){ this.providers=providers; }

    public EmailSendResult send(String to, String subject, String html){
        EmailSendResult last = null;
        for(EmailProvider p: providers){
            EmailSendResult r = p.send(to, subject, html);
            if(r.success()) return r;
            last = r;
            if(r.type()== EmailSendResult.ResultType.PERMANENT_FAILURE || r.type()== EmailSendResult.ResultType.SUCCESS){
                return r; // don't try further if permanent
            }
            // transient/config -> try next
        }
        return last==null? new EmailSendResult(false, EmailSendResult.ResultType.TRANSIENT_FAILURE, "No provider attempted", null, "none") : last;
    }
}
