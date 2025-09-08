package com.emailMarketing.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Aspect
@Component
public class ApiLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(ApiLoggingAspect.class);

    @Value("${logging.api.enabled:true}")
    private boolean apiLoggingEnabled;

    @Value("${logging.api.maxBody:4096}")
    private int maxBody;

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerBean() {}

    @Pointcut("execution(public * com.emailMarketing..*Controller.*(..))")
    public void anyControllerMethod() {}

    @Around("restControllerBean() && anyControllerMethod()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        if (!apiLoggingEnabled) {
            return pjp.proceed();
        }

        long start = System.currentTimeMillis();
        HttpServletRequest req = currentRequest();
        String method = req != null ? req.getMethod() : "N/A";
        String path = req != null ? req.getRequestURI() : pjp.getSignature().toShortString();
        String user = currentUsername();
        String argsSummary = summarizeArgs(pjp.getArgs());

        log.info("[API] --> {} {} user={} args={}", method, path, user, argsSummary);
        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            String resSummary = summarizeResult(result);
            log.info("[API] <-- {} {} user={} took={}ms result={}", method, path, user, elapsed, resSummary);
            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[API] !! {} {} user={} took={}ms error={}", method, path, user, elapsed, ex.toString());
            throw ex;
        }
    }

    @AfterThrowing(pointcut = "restControllerBean() && anyControllerMethod()", throwing = "ex")
    public void logExceptions(Throwable ex) {
        HttpServletRequest req = currentRequest();
        if (req != null) {
            log.warn("[API] EX {} {} -> {}", req.getMethod(), req.getRequestURI(), ex.toString());
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return String.valueOf(auth.getName());
        }
        return "anonymous";
    }

    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        List<String> parts = new ArrayList<>();
        for (Object a : args) {
            if (a == null) { parts.add("null"); continue; }
            if (a instanceof HttpServletRequest) { parts.add("HttpServletRequest"); continue; }
            if (a instanceof MultipartFile f) { parts.add("MultipartFile{name=" + f.getOriginalFilename() + ", size=" + f.getSize() + "}"); continue; }
            if (a instanceof byte[]) { parts.add("byte[" + ((byte[]) a).length + "]"); continue; }
            if (a instanceof Collection<?> c) { parts.add("Collection[size=" + c.size() + "]"); continue; }
            if (a instanceof Map<?,?> m) { parts.add(truncate(redactMap(m))); continue; }
            String s = truncate(String.valueOf(a));
            parts.add(s);
        }
        return parts.stream().collect(Collectors.joining(", ", "[", "]"));
    }

    private String summarizeResult(Object result) {
        if (result == null) return "null";
        if (result instanceof ResponseEntity<?> re) {
            Object body = re.getBody();
            String bodyStr = body == null ? "null" : truncate(String.valueOf(body));
            return "ResponseEntity{status=" + re.getStatusCode() + ", body=" + bodyStr + "}";
        }
        if (result instanceof Collection<?> c) {
            return "Collection[size=" + c.size() + "]";
        }
        if (result instanceof Map<?,?> m) {
            return truncate(redactMap(m));
        }
        return truncate(String.valueOf(result));
    }

    private String redactMap(Map<?,?> m) {
        try {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (isSensitiveKey(k)) {
                    copy.put(k, "***");
                } else if (v instanceof Map<?,?> nested) {
                    copy.put(k, redactMap(nested));
                } else {
                    copy.put(k, v);
                }
            }
            return truncate(copy.toString());
        } catch (Exception ex) {
            return truncate(m.toString());
        }
    }

    private boolean isSensitiveKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("password") || k.contains("authorization") || k.contains("token") || k.contains("secret");
    }

    private String truncate(String s) {
        if (s == null) return null;
        if (s.length() <= maxBody) return s;
        return s.substring(0, Math.max(0, maxBody)) + "…(truncated)";
    }
}
