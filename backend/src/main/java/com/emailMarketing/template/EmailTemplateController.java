package com.emailMarketing.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.emailMarketing.subscription.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class EmailTemplateController {
    private static final Logger log = LoggerFactory.getLogger(EmailTemplateController.class);
    private final EmailTemplateService service;
    private final UserRepository userRepository;
    private final VariableAnalysisService variableAnalysisService;
    private final com.emailMarketing.ai.GroqAiService groqAiService;

    public EmailTemplateController(EmailTemplateService service, UserRepository userRepository,
            VariableAnalysisService variableAnalysisService,
            com.emailMarketing.ai.GroqAiService groqAiService) {
        this.service = service;
        this.userRepository = userRepository;
        this.variableAnalysisService = variableAnalysisService;
        this.groqAiService = groqAiService;
    }

    public record TemplateRequest(String name, String html, String mjmlSource, Long categoryId, String description, String tags,
        Boolean shared, String easyEmailDesignJson) {
    }

    public record DuplicateResponse(Long newId) {
    }

    public record PreviewRequest(java.util.Map<String, Object> data) {
    }

    public record PreviewResponse(String html) {
    }

    public record CategoryRequest(String name) {
    }

    public record VariableRequest(String name, String defaultValue, Boolean required) {
    }

    public record VariableUpdateRequest(String defaultValue, Boolean required) {}

    public record VariableResponse(Long id, String name, String defaultValue, boolean required, boolean system) {
    }

    public record AssetResponse(Long id, String fileName, String contentType, Long sizeBytes, String storageKey) {
    }
    public record AssetUrlResponse(String url) {}

    public record AIDraftRequest(String prompt, String tone, String audience, String callToAction) {
    }

    public record AIDraftResponse(String html, List<String> variables, String model) {
    }

    public record AiMetaResponse(String json) {}
    public record AiMetaUpdateRequest(String json) {}

    @GetMapping
    public List<EmailTemplate> list(@AuthenticationPrincipal UserDetails principal) {
        return service.list(resolveUserId(principal));
    }

    @GetMapping("/accessible")
    public List<EmailTemplate> accessible(@AuthenticationPrincipal UserDetails principal) {
        return service.listAccessible(resolveUserId(principal));
    }

    @GetMapping("/shared")
    public List<EmailTemplate> shared() {
        return service.listShared();
    }

    @PostMapping
    public EmailTemplate create(@AuthenticationPrincipal UserDetails principal, @RequestBody TemplateRequest req) {
        EmailTemplate t = new EmailTemplate();
        t.setUserId(resolveUserId(principal));
        t.setName(req.name());
        // Oracle treats empty strings as NULL for many types; ensure we never violate NOT NULL
        // constraint on html by substituting a lightweight placeholder when blank.
        String rawHtml = req.html();
        if (rawHtml == null || rawHtml.trim().isEmpty()) {
            rawHtml = "<!-- empty template -->"; // placeholder persisted; frontend can treat as empty
        }
        t.setHtml(rawHtml);
        // Store raw MJML if provided (canonical); html snapshot will be rendered in service.save
        if (req.mjmlSource() != null && !req.mjmlSource().isBlank()) {
            t.setMjmlSource(req.mjmlSource());
        }
        t.setCategoryId(req.categoryId());
        t.setDescription(req.description());
        t.setTags(req.tags());
        if (Boolean.TRUE.equals(req.shared()))
            t.setShared(true);
        t.setEasyEmailDesignJson(req.easyEmailDesignJson());
        return service.save(t);
    }

    @GetMapping("/{id}")
    public EmailTemplate get(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) throws Exception {
        EmailTemplate t = service.get(id).orElseThrow();
        // Convert MJML to design JSON on-the-fly, but guard against malformed input (e.g., stray BOM/content causing SAX errors)
        String mjmlSrc = t.getMjmlSource();
        if (mjmlSrc != null && !mjmlSrc.isBlank()) {
            try {
                var node = PureJavaMjmlToEasyJsonExtended.convert(mjmlSrc);
                t.setEasyEmailDesignJson(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node));
            } catch (Exception ex) {
                log.warn("Failed to convert MJML for template {}: {}", id, ex.getMessage());
                // fallback minimal design JSON
                var fallback = PureJavaMjmlToEasyJsonExtended.minimalFallbackPage();
                t.setEasyEmailDesignJson(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(fallback));
            }
        } else if (t.getEasyEmailDesignJson() == null || t.getEasyEmailDesignJson().isBlank()) {
            // Provide at least an empty page JSON if neither MJML nor existing design JSON present
            var fallback = PureJavaMjmlToEasyJsonExtended.minimalFallbackPage();
            t.setEasyEmailDesignJson(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(fallback));
        }
        if (!t.getUserId().equals(resolveUserId(principal)) && !t.isShared())
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        return t;
    }

    @PutMapping("/{id}")
    public EmailTemplate update(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestBody TemplateRequest req) {
        EmailTemplate t = service.get(id).orElseThrow();
        Long uid = resolveUserId(principal);
        if (!t.getUserId().equals(uid))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        t.setName(req.name());
        String rawHtml = req.html();
        if (rawHtml == null || rawHtml.trim().isEmpty()) {
            rawHtml = "<!-- empty template -->";
        }
        t.setHtml(rawHtml);
        if (req.mjmlSource() != null && !req.mjmlSource().isBlank()) {
            t.setMjmlSource(req.mjmlSource());
        }
        t.setCategoryId(req.categoryId());
        t.setDescription(req.description());
        t.setTags(req.tags());
        if (req.shared() != null)
            t.setShared(req.shared());
        t.setEasyEmailDesignJson(req.easyEmailDesignJson());
        return service.save(t);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        Long uid = resolveUserId(principal);
        service.deleteTemplate(id, uid);
    }

    // --- AI Meta ---
    @GetMapping("/{id}/ai-meta")
    public AiMetaResponse getAiMeta(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        Long uid = resolveUserId(principal);
        var jsonOpt = service.aiMeta(id, uid);
        return new AiMetaResponse(jsonOpt.orElse(null));
    }

    @PutMapping("/{id}/ai-meta")
    public AiMetaResponse updateAiMeta(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id, @RequestBody AiMetaUpdateRequest req) {
        Long uid = resolveUserId(principal);
        String json = service.updateAiMeta(id, uid, req.json());
        return new AiMetaResponse(json);
    }

    @PostMapping("/{id}/duplicate")
    public DuplicateResponse duplicate(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        Long uid = resolveUserId(principal);
        EmailTemplate copy = service.duplicate(id, uid);
        return new DuplicateResponse(copy.getId());
    }

    @PostMapping("/{id}/preview")
    public PreviewResponse preview(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestBody PreviewRequest req) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)) && !t.isShared())
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        String rendered = service.preview(t, req.data() == null ? java.util.Map.of() : req.data());
        return new PreviewResponse(rendered);
    }

    @PostMapping("/{id}/test-send")
    public java.util.Map<String, String> testSend(@AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        return java.util.Map.of("result", service.testSendPlaceholder());
    }

    // Versions
    @GetMapping("/{id}/versions")
    public List<com.emailMarketing.template.model.TemplateVersion> versions(
            @AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)) && !t.isShared())
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        return service.versions(id);
    }

    // Variables
    @GetMapping("/{id}/variables")
    public List<VariableResponse> variables(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
    return service.variables(id).stream()
        .map(v -> new VariableResponse(v.getId(), v.getVarName(), v.getDefaultValue(), v.isRequired(), v.isSystem()))
                .toList();
    }

    @PostMapping("/{id}/variables")
    public VariableResponse addVariable(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestBody VariableRequest req) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        var v = service.registerVariable(id, req.name(), req.defaultValue(), Boolean.TRUE.equals(req.required()));
    return new VariableResponse(v.getId(), v.getVarName(), v.getDefaultValue(), v.isRequired(), v.isSystem());
    }

    @DeleteMapping("/{id}/variables/{varId}")
    public void deleteVariable(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @PathVariable Long varId) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        service.deleteVariable(id, varId);
    }

    @PatchMapping("/{id}/variables/{varId}")
    public VariableResponse updateVariable(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @PathVariable Long varId, @RequestBody VariableUpdateRequest req) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        var updated = service.updateVariable(id, varId, req.defaultValue(), req.required());
    return new VariableResponse(updated.getId(), updated.getVarName(), updated.getDefaultValue(), updated.isRequired(), updated.isSystem());
    }

    // Categories
    @GetMapping("/categories")
    public List<com.emailMarketing.template.model.TemplateCategory> categories(
            @AuthenticationPrincipal UserDetails principal) {
        return service.categories(resolveUserId(principal));
    }

    @PostMapping("/categories")
    public com.emailMarketing.template.model.TemplateCategory createCategory(
            @AuthenticationPrincipal UserDetails principal, @RequestBody CategoryRequest req) {
        return service.createCategory(resolveUserId(principal), req.name());
    }

    // Assets
    @GetMapping("/{id}/assets")
    public List<AssetResponse> assets(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        return service.assets(id).stream().map(a -> new AssetResponse(a.getId(), a.getFileName(), a.getContentType(),
                a.getSizeBytes(), a.getStorageKey())).toList();
    }

    @PostMapping("/{id}/assets")
    public AssetResponse uploadAsset(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        var a = service.addAsset(id, file);
        return new AssetResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getStorageKey());
    }

    // Generate a temporary read URL for an asset
    @GetMapping("/{id}/assets/{assetId}/url")
    public AssetUrlResponse getAssetUrl(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @PathVariable Long assetId,
            @RequestParam(name = "expiryMinutes", required = false, defaultValue = "5") int expiryMinutes) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        String url = service.generateAssetReadUrl(id, assetId, expiryMinutes)
                .orElse(null);
        return new AssetUrlResponse(url);
    }

    // Folder-specific uploads (store files under different logical folders)
    @PostMapping("/{id}/assets/images")
    public AssetResponse uploadImageAsset(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        var a = service.addAssetToFolder(id, "images", file);
        return new AssetResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getStorageKey());
    }

    @PostMapping("/{id}/assets/logos")
    public AssetResponse uploadLogoAsset(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        var a = service.addAssetToFolder(id, "logos", file);
        return new AssetResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getStorageKey());
    }

    @PostMapping("/{id}/assets/brand")
    public AssetResponse uploadBrandAsset(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        var a = service.addAssetToFolder(id, "brand", file);
        return new AssetResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getStorageKey());
    }

    @GetMapping("/{id}/analyze")
    public java.util.Map<String, Object> analyze(@AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id) {
        EmailTemplate t = service.get(id).orElseThrow();
        if (!t.getUserId().equals(resolveUserId(principal)) && !t.isShared())
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        var res = variableAnalysisService.analyze(t.getHtml());
        return java.util.Map.of("variables", res.variables(), "missingRequired", res.missingRequired(), "warnings",
                res.warnings());
    }

    // AI draft generation (placeholder implementation)
    @PostMapping("/ai/draft")
    public AIDraftResponse aiDraft(@AuthenticationPrincipal UserDetails principal, @RequestBody AIDraftRequest req) {
        String safePrompt = (req.prompt() == null || req.prompt().isBlank()) ? "New marketing email"
                : req.prompt().trim();
        String tone = req.tone() == null ? "friendly" : req.tone();
        String audience = req.audience() == null ? "customers" : req.audience();
        String cta = req.callToAction() == null ? "Click here" : req.callToAction();

        String html;
        String modelUsed;
        if (groqAiService != null && groqAiService.isConfigured()) {
            try {
                html = groqAiService.generateEmailHtml(safePrompt, tone, audience, cta);
                modelUsed = groqAiService.getModel();
            } catch (Exception ex) {
                log.warn("AI draft via Python service failed, using placeholder: {}", ex.getMessage());
                html = placeholderHtml(safePrompt, audience, tone, cta);
                modelUsed = "placeholder-local";
            }
        } else {
            // Fallback to existing placeholder content if Groq isn’t configured
            html = placeholderHtml(safePrompt, audience, tone, cta);
            modelUsed = "placeholder-local";
        }

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}").matcher(html);
        java.util.Set<String> vars = new java.util.LinkedHashSet<>();
        while (m.find()) vars.add(m.group(1));
        return new AIDraftResponse(html, java.util.List.copyOf(vars), modelUsed);
    }

    private String escape(String in) {
        return in.replace("<", "&lt;").replace(">", "&gt;");
    }

    private String placeholderHtml(String safePrompt, String audience, String tone, String cta) {
        return "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='max-width:600px;margin:auto;font-family:Arial,sans-serif'>"
                +
                "<tr><td style='padding:24px;background:#0d47a1;color:#fff;text-align:center'><h1 style='margin:0;font-size:24px'>"
                + escape(safePrompt) + "</h1></td></tr>" +
                "<tr><td style='padding:24px;background:#ffffff;color:#222;font-size:14px;line-height:1.5'>" +
                "<p>Hi {{first_name}},</p>" +
                "<p>We're reaching out to our " + escape(audience) + " with a " + escape(tone) + " update.</p>" +
                "<p>" + escape(safePrompt) + " – crafted just for you.</p>" +
                "<p style='text-align:center;margin:32px 0'><a href='{{primary_link}}' style='display:inline-block;background:#0d47a1;color:#fff;padding:12px 20px;border-radius:4px;text-decoration:none'>"
                + escape(cta) + "</a></p>" +
                "<p>If you have any questions, just reply to this email.</p>" +
                "<p style='font-size:12px;color:#666'>If you no longer wish to receive these emails you can {{unsubscribe}}.</p>" +
                "</td></tr></table>";
    }

    private Long resolveUserId(UserDetails principal) {
        return userRepository.findByUsername(principal.getUsername()).map(u -> u.getId()).orElseThrow();
    }
}
