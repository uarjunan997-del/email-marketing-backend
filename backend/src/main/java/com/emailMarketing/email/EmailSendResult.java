package com.emailMarketing.email;

import java.time.Duration;

public record EmailSendResult(boolean success, ResultType type, String message, Duration retryAfter, String provider) {
    public enum ResultType { SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE, CONFIG_ERROR }
}
