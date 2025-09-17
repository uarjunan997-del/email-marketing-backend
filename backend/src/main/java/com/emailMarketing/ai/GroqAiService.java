package com.emailMarketing.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GroqAiService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${ai.python.base-url:http://localhost:8000}")
    private String pythonBaseUrl;

    private volatile String lastModelUsed = "python-groq";

    public GroqAiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String getModel() { return lastModelUsed; }

    public boolean isConfigured() { return pythonBaseUrl != null && !pythonBaseUrl.isBlank(); }

    public String generateEmailHtml(String prompt, String tone, String audience, String callToAction) {
        try {
            return generateEmailHtmlAsync(prompt, tone, audience, callToAction)
                    .orTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            throw new RuntimeException("AI draft generation failed via Python service: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch a full draft (html + optional mjml + variables + model) without collapsing to HTML only.
     * Falls back to placeholder HTML wrapper around MJML if html missing.
     */
    public DraftResponse generateDraft(String prompt, String tone, String audience, String callToAction) {
        if (!isConfigured()) {
            throw new IllegalStateException("Python AI service base URL not configured");
        }
        try {
            DraftRequest body = new DraftRequest(safe(prompt), safe(tone), safe(audience), safe(callToAction));
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonBaseUrl.replaceAll("/+$", "") + "/ai/draft"))
                    .timeout(Duration.ofSeconds(55))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int sc = resp.statusCode();
            if (sc < 200 || sc >= 300) {
                throw new RuntimeException("Python AI service error: status=" + sc + ", body=" + resp.body());
            }
            DraftResponse dr = objectMapper.readValue(resp.body(), DraftResponse.class);
            if ((dr.html == null || dr.html.isBlank()) && (dr.mjml == null || dr.mjml.isBlank())) {
                throw new RuntimeException("Python AI service returned neither html nor mjml");
            }
            if (dr.model != null && !dr.model.isBlank()) {
                lastModelUsed = dr.model;
            }
            // If html missing but MJML present, keep MJML (canonical elsewhere) and provide placeholder html
            if ((dr.html == null || dr.html.isBlank()) && dr.mjml != null) {
                dr.html = mjmlToPlaceholderHtml(dr.mjml);
            }
            return dr;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("AI draft generation failed via Python service: " + e.getMessage(), e);
        }
    }

    public CompletableFuture<String> generateEmailHtmlAsync(String prompt, String tone, String audience, String callToAction) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Python AI service base URL not configured"));
        }
        try {
            DraftRequest body = new DraftRequest(safe(prompt), safe(tone), safe(audience), safe(callToAction));
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonBaseUrl.replaceAll("/+$", "") + "/ai/draft"))
                    .timeout(Duration.ofSeconds(55))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(resp -> {
                        int sc = resp.statusCode();
                        if (sc < 200 || sc >= 300) {
                            throw new RuntimeException("Python AI service error: status=" + sc + ", body=" + resp.body());
                        }
                        try {
                            DraftResponse dr = objectMapper.readValue(resp.body(), DraftResponse.class);
                            if ((dr.html == null || dr.html.isBlank()) && (dr.mjml == null || dr.mjml.isBlank())) {
                                throw new RuntimeException("Python AI service returned neither html nor mjml");
                            }
                            if (dr.model != null && !dr.model.isBlank()) {
                                lastModelUsed = dr.model;
                            }
                            // Prefer html if provided; otherwise render MJML or fallback convert MJML to html upstream later.
                            if (dr.html != null && !dr.html.isBlank()) {
                                return dr.html.trim();
                            }
                            // If only MJML came back, do a naive unwrap (we keep MJML canonical elsewhere).
                            return mjmlToPlaceholderHtml(dr.mjml);
                        } catch (Exception ex) {
                            throw new RuntimeException("Failed to parse Python AI response: " + ex.getMessage(), ex);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String mjmlToPlaceholderHtml(String mjml) {
        if (mjml == null) return "";
        // Minimal fallback: extract mj-text sections or just wrap raw.
        try {
            String cleaned = mjml.replaceAll("(?is)<script.*?</script>", "");
            return "<!-- Fallback HTML from MJML (full render to be done with mjml4j) -->\n" + cleaned;
        } catch (Exception e) {
            return mjml;
        }
    }

    private String safe(String s) { return s == null ? "" : s; }

    // Optional local extraction if needed elsewhere
    public static List<String> extractVariables(String html) {
        Pattern p = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}");
        Matcher m = p.matcher(html);
        Set<String> vars = new java.util.LinkedHashSet<>();
        while (m.find()) vars.add(m.group(1));
        return java.util.List.copyOf(vars);
    }

    // DTOs for Python service
    record DraftRequest(
            @JsonProperty("prompt") String prompt,
            @JsonProperty("tone") String tone,
            @JsonProperty("audience") String audience,
            @JsonProperty("callToAction") String callToAction
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DraftResponse {
        @JsonProperty("html") public String html;
        @JsonProperty("mjml") public String mjml; // optional new field
        @JsonProperty("variables") public List<String> variables;
        @JsonProperty("model") public String model;
    }
}
