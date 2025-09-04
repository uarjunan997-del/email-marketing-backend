package com.emailMarketing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    private ErrorResponse build(HttpStatus status, String message, String code, String path, List<String> details) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, code, (details == null || details.isEmpty()) ? null : details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegal(IllegalArgumentException ex, HttpServletRequest req){
        return ResponseEntity.badRequest().body(build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", req.getRequestURI(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleState(IllegalStateException ex, HttpServletRequest req){
        String code = switch(ex.getMessage()){
            case "EMAIL_NOT_VERIFIED" -> "EMAIL_NOT_VERIFIED";
            case "USER_DISABLED" -> "USER_DISABLED";
            case "RATE_LIMIT_EXCEEDED" -> "RATE_LIMIT";
            default -> "ILLEGAL_STATE";
        };
        HttpStatus status = code.equals("RATE_LIMIT") ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(build(status, ex.getMessage(), code, req.getRequestURI(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "INTERNAL_ERROR", req.getRequestURI(), null));
    }
}
