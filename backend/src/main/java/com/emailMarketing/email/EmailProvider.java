package com.emailMarketing.email;

public interface EmailProvider {
    EmailSendResult send(String to, String subject, String html);
    String name();
}
