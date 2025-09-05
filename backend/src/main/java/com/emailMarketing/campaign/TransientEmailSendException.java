package com.emailMarketing.campaign;

public class TransientEmailSendException extends RuntimeException {
    public TransientEmailSendException(String message){ super(message); }
    public TransientEmailSendException(String message, Throwable cause){ super(message, cause); }
}
