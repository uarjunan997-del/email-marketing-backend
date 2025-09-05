package com.emailMarketing.campaign;

public class PermanentEmailSendException extends RuntimeException {
    public PermanentEmailSendException(String message){ super(message); }
    public PermanentEmailSendException(String message, Throwable cause){ super(message, cause); }
}
