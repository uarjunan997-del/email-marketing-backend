package com.emailMarketing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.DataAccessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    private ErrorResponse build(HttpStatus status, String message, String code, String path, List<String> details) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, code, (details == null || details.isEmpty()) ? null : details);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest req){
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(build(HttpStatus.BAD_REQUEST, "Database error: " + msg, "DB_ERROR", req.getRequestURI(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest req){
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(build(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "FILE_TOO_LARGE", req.getRequestURI(), null));
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMultipart(Exception ex, HttpServletRequest req){
        return ResponseEntity.badRequest()
                .body(build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", req.getRequestURI(), null));
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
