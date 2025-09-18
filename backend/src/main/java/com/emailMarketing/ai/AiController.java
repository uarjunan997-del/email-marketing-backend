package com.emailMarketing.ai;

import com.emailMarketing.template.EmailTemplate;
import com.emailMarketing.template.EmailTemplateService;
import com.emailMarketing.subscription.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.emailMarketing.template.model.TemplateAsset;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final GroqAiService groqAiService;
    private final EmailTemplateService templateService;
    private final UserRepository userRepository;

    public AiController(GroqAiService groqAiService, EmailTemplateService templateService, UserRepository userRepository) {
        this.groqAiService = groqAiService;
        this.templateService = templateService;
        this.userRepository = userRepository;
    }

    public record GeneratedTemplate(String id, String html, String mjml, String css, String previewImage,
                                    Map<String, String> assetUrls,
                                    Meta metadata) {}
    public record Meta(String title, String description, List<String> usedAssets) {}
    public record AiTemplateResponse(GeneratedTemplate template, List<String> suggestions, long processingTime) {}

    @PostMapping(path = "/generate-template-with-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiTemplateResponse generateTemplateWithAssets(
            @AuthenticationPrincipal UserDetails principal,
            MultipartHttpServletRequest request,
            @RequestParam(value = "templateId", required = false) Long templateId,
            @RequestParam("prompt") String prompt,
            @RequestParam("templateType") String templateType,
            @RequestParam(value = "style", required = false) String style,
            @RequestParam(value = "brandColors", required = false) String brandColorsJson
    ) {
        long start = System.currentTimeMillis();
        String userPart = principal != null ? principal.getUsername() : "anon";
        String day = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).substring(0,10);
        String basePrefix = String.format("ai/%s/%s/%s", userPart, day, UUID.randomUUID());

        // Collect files: keys like assets[0], assets[1]
        MultiValueMap<String, MultipartFile> fileMap = request.getMultiFileMap();
        Map<String, String> assetUrls = new LinkedHashMap<>();
        List<String> usedAssets = new ArrayList<>();

        Pattern p = Pattern.compile("assets\\[(\\d+)]");

        // If templateId provided, verify ownership
        EmailTemplate template = null;
        Long userId = null;
        Map<String, TemplateAsset> existingAssetsByFolderAndFile = Collections.emptyMap();
        if (templateId != null) {
            template = templateService.get(templateId).orElseThrow();
            if (principal == null) throw new org.springframework.security.access.AccessDeniedException("Forbidden");
            userId = userRepository.findByUsername(principal.getUsername()).map(u -> u.getId()).orElse(null);
            if (userId == null || !template.getUserId().equals(userId)) {
                throw new org.springframework.security.access.AccessDeniedException("Forbidden");
            }
            // Build lookup to avoid re-uploading the same files (already uploaded via individual endpoints)
            List<com.emailMarketing.template.model.TemplateAsset> existing = templateService.assets(templateId);
            Map<String, TemplateAsset> map = new HashMap<>();
            Pattern folderPattern = Pattern.compile("templates/\\d+/([a-zA-Z0-9_-]+)/");
            for (TemplateAsset ta : existing) {
                String storageKey = ta.getStorageKey();
                if (storageKey == null) continue;
                Matcher fm = folderPattern.matcher(storageKey);
                String folder = fm.find() ? fm.group(1) : "misc";
                String sanitizedName = (ta.getFileName() == null ? "file" : ta.getFileName()).replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase();
                map.putIfAbsent(folder + "::" + sanitizedName, ta);
            }
            existingAssetsByFolderAndFile = map;
        }

    String firstLogoUrl = null;
    List<String> contentImageUrls = new ArrayList<>();
    for (String key : fileMap.keySet()) {
            Matcher m = p.matcher(key);
            if (!m.matches()) continue;
            int idx = Integer.parseInt(m.group(1));
            MultipartFile file = Optional.ofNullable(fileMap.getFirst(key)).orElse(null);
            if (file == null || file.isEmpty()) continue;

            String assetName = Optional.ofNullable(request.getParameter("assetNames[" + idx + "]")).orElse(file.getOriginalFilename());
            String assetType = Optional.ofNullable(request.getParameter("assetTypes[" + idx + "]")).orElse("misc");
            String safeName = (assetName == null ? "file" : assetName).replaceAll("[^a-zA-Z0-9._-]", "-");
            try {
                if (template != null) {
                    String folder = switch (assetType.toLowerCase()) {
                        case "logo" -> "logos";
                        case "background", "content", "image", "images" -> "images";
                        case "brand" -> "brand";
                        default -> "misc";
                    };
                    String lookupKey = folder + "::" + safeName.toLowerCase();
                    TemplateAsset existingAsset = existingAssetsByFolderAndFile.get(lookupKey);
                    TemplateAsset effective = existingAsset;
                    if (existingAsset == null) {
                        // Only upload if not already present (new asset not uploaded via dedicated endpoint)
                        effective = templateService.addAssetToFolder(templateId, folder, file);
                        existingAssetsByFolderAndFile.put(lookupKey, effective);
                    }
                    String url = effective.getId() != null ? templateService.generateAssetReadUrl(templateId, effective.getId(), 0).orElse(effective.getStorageKey()) : effective.getStorageKey();
                    if (url != null) assetUrls.put(assetName, url);
                    if ("logo".equalsIgnoreCase(assetType) && firstLogoUrl == null) {
                        firstLogoUrl = url;
                    }
                    if (("content".equalsIgnoreCase(assetType) || "background".equalsIgnoreCase(assetType) || "image".equalsIgnoreCase(assetType)) && url != null) {
                        contentImageUrls.add(url);
                    }
                } else {
                    String keyPrefix = basePrefix + "/" + assetType + "/";
                    String objectKey = keyPrefix + UUID.randomUUID() + "-" + safeName;
                    assetUrls.put(assetName, objectKey);
                }
                usedAssets.add(assetName);
            } catch (Exception e) {
                log.warn("Failed to process asset {}: {}", assetName, e.toString());
            }
        }

        // Call AI generator via Python service, derive tone/style heuristics
        String tone = switch (Optional.ofNullable(style).orElse("modern").toLowerCase()) {
            case "classic" -> "professional";
            case "minimal" -> "concise";
            case "bold" -> "energetic";
            default -> "friendly";
        };
        String audience = templateType;
        String cta = "Learn more";

        // Parse brand colors if provided (JSON array of hex strings)
        List<String> brandColors = new ArrayList<>();
        if (brandColorsJson != null && !brandColorsJson.isBlank()) {
            try {
                String jsonTrim = brandColorsJson.trim();
                if (jsonTrim.startsWith("[") && jsonTrim.endsWith("]")) {
                    // naive split parsing to avoid pulling in full JSON libs (ObjectMapper already exists but keep light)
                    String inner = jsonTrim.substring(1, jsonTrim.length()-1);
                    for (String part : inner.split(",")) {
                        String c = part.replaceAll("[\"'\\s]", "").toLowerCase();
                        if (c.matches("#?[0-9a-f]{3,8}")) {
                            if (!c.startsWith("#")) c = "#" + c;
                            brandColors.add(c);
                        }
                    }
                }
            } catch (Exception ignore) {}
        }
        String primaryColor = brandColors.isEmpty() ? "#2563eb" : brandColors.get(0);
        String primaryTextColor = getContrastColor(primaryColor);

        // Augment prompt with asset context so AI can reference placeholders
        StringBuilder promptAug = new StringBuilder(prompt == null ? "" : prompt.trim());
        if (!brandColors.isEmpty()) {
            promptAug.append("\nPrimary brand color: ").append(primaryColor).append(". Use it for buttons and prominent accents. Ensure sufficient contrast.");
            if (brandColors.size() > 1) {
                // Provide full palette guidance to model
                String paletteList = String.join(", ", brandColors);
                String secondary = brandColors.size() > 1 ? brandColors.get(1) : primaryColor;
                String accent = brandColors.size() > 2 ? brandColors.get(2) : secondary;
                promptAug.append("\nFull brand palette: [").append(paletteList).append("] (treat first as primary, second as secondary background/section divider, third as accent for badges or subtle highlights). Avoid heavy gradients unless requested; keep CTA buttons using primary background and readable text (auto-contrast). Do not introduce new arbitrary colors.");
                promptAug.append(" Use secondary color (e.g., ").append(secondary).append(") for section headers or subtle horizontal rules; use accent (e.g., ").append(accent).append(") sparingly for badges, small icons, or highlight spans.");
            }
        }
    if (firstLogoUrl != null) {
        // Provide actual URL so AI can infer possible brand characteristics (colors, aspect) if model can fetch; still require placeholder usage in final HTML.
        promptAug.append("\nA brand logo is available at: ").append(firstLogoUrl)
            .append(". In the layout, place the logo in the header aligned to the right (not centered). " +
                "Do NOT inline the raw URL in final HTML; instead use the placeholder {{logo_url}} as the img src. Max width ~200px, preserve aspect ratio, add alt text 'Logo'.");
    }
        String heroImageUrl = null;
        if (!contentImageUrls.isEmpty()) {
            heroImageUrl = contentImageUrls.get(0); // treat first as hero
            promptAug.append("\nA primary hero image is available at: ").append(heroImageUrl)
                    .append(". Use it as a full-width (max 600px container) hero section with a background image, subtle overlay for text readability if needed (you may simulate with a semi-transparent layer). Do NOT inline the raw URL directly; in final HTML use the placeholder {{hero_image}} as the background or img src. Provide accessible alt text (e.g., 'Hero image').");
            if (contentImageUrls.size() > 0) {
                // content_image_1 duplicates hero for alternate inline usage
                promptAug.append(" The same hero image is also addressable via {{content_image_1}} if a smaller inline rendition is needed later in the email.");
            }
            if (contentImageUrls.size() > 1) {
                promptAug.append(" Additional secondary images are available: ");
                for (int i = 1; i < contentImageUrls.size(); i++) {
                    promptAug.append("{{content_image_").append(i+1).append("}} ");
                }
                promptAug.append("— use them for product features, a 2-column grid, testimonials, or icon-style callouts. Keep total image weight reasonable and avoid decorative redundancy.");
            }
            promptAug.append(" Always use the placeholder tokens ({{hero_image}}, {{content_image_n}}) instead of raw URLs in the HTML.");
            for (int i = 0; i < contentImageUrls.size(); i++) {
                assetUrls.putIfAbsent("content_image_" + (i+1), contentImageUrls.get(i));
            }
        }
        String html;
        String mjml = null;
        try {
            // New pathway: request full draft (html + mjml)
            var draft = groqAiService.generateDraft(promptAug.toString(), tone, audience, cta);
            html = draft.html;
            mjml = draft.mjml; // may be null
        } catch (Exception e) {
            log.warn("AI generation failed: {}", e.toString());
            html = fallbackHtml(prompt);
        }

        // Inject logo placeholder at top if not already present
        if (firstLogoUrl != null) {
            assetUrls.putIfAbsent("logo_url", firstLogoUrl); // canonical key
            if (!html.contains("{{logo_url}}")) {
                // Right-aligned header logo (only inject if model omitted it)
                String snippet = "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 16px 0;\"><tr><td style=\"text-align:right;padding:0 0 8px 0;\"><img src=\"{{logo_url}}\" alt=\"Logo\" style=\"max-width:200px;height:auto;display:inline-block;\"></td></tr></table>";
                int bodyIdx = html.toLowerCase().indexOf("<body");
                if (bodyIdx >= 0) {
                    int close = html.indexOf('>', bodyIdx);
                    if (close > bodyIdx) {
                        html = html.substring(0, close + 1) + snippet + html.substring(close + 1);
                    } else {
                        html = snippet + html; // fallback
                    }
                } else {
                    html = snippet + html;
                }
            }
        }

        // Inject hero section if hero image available and placeholder absent
        if (heroImageUrl != null) {
            assetUrls.putIfAbsent("hero_image", heroImageUrl);
            if (!html.contains("{{hero_image}}")) {
                String hero = "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 auto;max-width:600px;\"><tr><td style=\"padding:0 0 32px 0;\"><div style=\"background:#222 url('{{hero_image}}') center/cover no-repeat;border-radius:8px;padding:60px 30px;text-align:center;color:#ffffff;\"><h1 style=\"margin:0 0 16px;font-size:28px;font-family:Arial,sans-serif;\">{{headline}}</h1><p style=\"margin:0 0 24px;font-size:16px;line-height:1.5;font-family:Arial,sans-serif;\">{{subheading}}</p><a href=\"{{primary_link}}\" style=\"display:inline-block;background:" + primaryColor + ";color:" + primaryTextColor + ";text-decoration:none;padding:14px 26px;border-radius:6px;font-weight:bold;font-family:Arial,sans-serif;\">{{cta_text}}</a></div></td></tr></table>";
                // Place hero after logo (if inserted) or after opening body
                int insertionPoint = html.indexOf("{{logo_url}}");
                if (insertionPoint >= 0) {
                    // find closing tag of the img container snippet we added
                    int afterLogo = html.indexOf("</div>", insertionPoint);
                    if (afterLogo > 0) {
                        html = html.substring(0, afterLogo + 6) + hero + html.substring(afterLogo + 6);
                    } else {
                        html = hero + html;
                    }
                } else {
                    int bodyIdx = html.toLowerCase().indexOf("<body");
                    if (bodyIdx >= 0) {
                        int close = html.indexOf('>', bodyIdx);
                        if (close > bodyIdx) {
                            html = html.substring(0, close + 1) + hero + html.substring(close + 1);
                        } else {
                            html = hero + html;
                        }
                    } else {
                        html = hero + html;
                    }
                }
            }
        }

        // Basic metadata
    String safePrompt = prompt == null ? "" : prompt;
    Meta meta = new Meta(
        "AI Generated: " + (safePrompt.length() > 60 ? safePrompt.substring(0,60) + "…" : safePrompt),
        "Template type: " + templateType + ", style: " + (style == null ? "modern" : style),
        usedAssets
    );
        // If a template was referenced, persist the generated HTML so editor opens populated
        if (template != null) {
            try {
                template.setHtml(html);
                if (mjml != null && !mjml.isBlank()) {
                    template.setMjmlSource(mjml);
                    // lastRenderedHtml will be set by service.save via MjmlRenderService
                }
                template.setDescription(meta.description());
                // Append ai-draft tag if absent
                String tags = template.getTags();
                if (tags == null || tags.isBlank()) {
                    template.setTags("ai-draft");
                } else if (!Arrays.stream(tags.split(",")).map(String::trim).anyMatch("ai-draft"::equalsIgnoreCase)) {
                    template.setTags(tags + ",ai-draft");
                }
                // Persist AI meta JSON (prompt, type, style, colors)
                try {
                    // Build lightweight JSON (avoid bringing in ObjectMapper here)
                    StringBuilder json = new StringBuilder();
                    json.append('{');
                    json.append("\"prompt\":\"").append(jsonEscape(safePrompt)).append('\"');
                    json.append(",\"type\":\"").append(jsonEscape(templateType)).append('\"');
                    json.append(",\"style\":\"").append(jsonEscape(style == null ? "modern" : style)).append('\"');
                    if (!brandColors.isEmpty()) {
                        json.append(",\"colors\":[");
                        for (int i=0;i<brandColors.size();i++) {
                            if (i>0) json.append(',');
                            json.append('\"').append(jsonEscape(brandColors.get(i))).append('\"');
                        }
                        json.append(']');
                    } else {
                        json.append(",\"colors\":[]");
                    }
                    json.append('}');
                    template.setAiMetaJson(json.toString());
                } catch (Exception ignore) {}
                templateService.save(template);
            } catch (Exception ex) {
                log.warn("Failed to persist generated HTML to template {}: {}", template.getId(), ex.toString());
            }
        }

    GeneratedTemplate tpl = new GeneratedTemplate(
        String.valueOf(template != null ? template.getId() : UUID.randomUUID()),
        html,
        mjml,
        "",
        "",
        assetUrls,
        meta
    );

        List<String> suggestions = List.of(
                "Replace placeholders like {{first_name}} and {{primary_link}}",
                "Verify image alt text and test dark-mode contrast",
                "Run a test send to yourself and check on mobile"
        );

        long ms = System.currentTimeMillis() - start;
        return new AiTemplateResponse(tpl, suggestions, ms);
    }

    @PostMapping(path = "/asset-suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> assetSuggestions(@RequestBody Map<String, Object> body) {
        // Simple stub that suggests common sections based on asset types
        List<String> suggestions = new ArrayList<>();
        try {
            Object assetsObj = body.get("assets");
            if (assetsObj instanceof List<?> list) {
                boolean hasLogo = list.stream().anyMatch(m -> (m instanceof Map<?,?> mm) && Objects.equals(mm.get("type"), "logo"));
                boolean hasBg = list.stream().anyMatch(m -> (m instanceof Map<?,?> mm) && Objects.equals(mm.get("type"), "background"));
                boolean hasContent = list.stream().anyMatch(m -> (m instanceof Map<?,?> mm) && Objects.equals(mm.get("type"), "content"));
                if (hasLogo) suggestions.add("Place the logo in the header with 24px padding");
                if (hasBg) suggestions.add("Use the background asset for the hero section with overlay text");
                if (hasContent) suggestions.add("Insert the content image next to a bullet list for details");
            }
        } catch (Exception ignored) {}
        return Map.of("suggestions", suggestions);
    }

    private String fallbackHtml(String prompt) {
        String safe = prompt == null ? "Your campaign" : prompt.replace("<", "&lt;").replace(">", "&gt;");
        return "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='max-width:600px;margin:auto;font-family:Arial,sans-serif'>" +
                "<tr><td style='padding:24px;background:#0d47a1;color:#fff;text-align:center'><h1 style='margin:0;font-size:24px'>" + safe + "</h1></td></tr>" +
                "<tr><td style='padding:24px;background:#ffffff;color:#222;font-size:14px;line-height:1.5'>" +
                "<p>Hi {{first_name}},</p>" +
                "<p>Welcome to your new email template.</p>" +
                "<p style='text-align:center;margin:32px 0'><a href='{{primary_link}}' style='display:inline-block;background:#0d47a1;color:#fff;padding:12px 20px;border-radius:4px;text-decoration:none'>Learn more</a></p>" +
                "<p style='font-size:12px;color:#666'>Unsubscribe here: {{unsubscribe}}</p>" +
                "</td></tr></table>";
    }

    // Simple luminance-based contrast chooser (#RRGGBB expected)
    private String getContrastColor(String hex) {
        if (hex == null || hex.length() < 4) return "#ffffff";
        String c = hex.startsWith("#") ? hex.substring(1) : hex;
        if (c.length() == 3) { // expand shorthand
            c = "" + c.charAt(0)+c.charAt(0)+c.charAt(1)+c.charAt(1)+c.charAt(2)+c.charAt(2);
        }
        try {
            int r = Integer.parseInt(c.substring(0,2),16);
            int g = Integer.parseInt(c.substring(2,4),16);
            int b = Integer.parseInt(c.substring(4,6),16);
            // Relative luminance approximation
            double luminance = (0.2126*r + 0.7152*g + 0.0722*b)/255.0;
            return luminance > 0.55 ? "#000000" : "#ffffff";
        } catch (Exception e) {
            return "#ffffff";
        }
    }

    // Minimal JSON string escaper (quotes, backslashes, control chars)
    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length()+16);
        for (int i=0;i<s.length();i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }
}
