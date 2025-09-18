package com.emailMarketing.template;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.emailMarketing.template.repo.*;
import com.emailMarketing.template.model.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.emailMarketing.storage.OciObjectStorageService;
import java.time.LocalDateTime;

@Service
public class EmailTemplateService {
    private final EmailTemplateRepository repo;
    private final TemplateVersionRepository versionRepo;
    private final TemplateVariableRepository variableRepo;
    private final TemplateRenderingService renderer;
    private final TemplateCategoryRepository categoryRepo;
    private final TemplateAssetRepository assetRepo;
    @Autowired(required = false)
    private OciObjectStorageService ociStorage;

    @Value("${app.assets.read-url-default-expiry-minutes:43200}")
    private int defaultAssetReadExpiryMinutes;
    private MjmlRenderService mjmlRenderService;

    public EmailTemplateService(EmailTemplateRepository repo, TemplateVersionRepository versionRepo,
            TemplateVariableRepository variableRepo, TemplateRenderingService renderer,
            TemplateCategoryRepository categoryRepo, TemplateAssetRepository assetRepo,
            MjmlRenderService mjmlRenderService) {
        this.repo = repo;
        this.versionRepo = versionRepo;
        this.variableRepo = variableRepo;
        this.renderer = renderer;
        this.categoryRepo = categoryRepo;
        this.assetRepo = assetRepo;
        this.mjmlRenderService = mjmlRenderService;
    }

    public List<EmailTemplate> list(Long userId) {
        return repo.findByUserId(userId);
    }

    public List<EmailTemplate> listAccessible(Long userId) {
        return repo.findAccessibleForUser(userId);
    }

    public List<EmailTemplate> listShared() {
        return repo.findByIsSharedTrue();
    }

    public Optional<EmailTemplate> get(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public String updateAiMeta(Long templateId, Long userId, String json) {
        EmailTemplate t = repo.findById(templateId).orElseThrow();
        if (!Objects.equals(t.getUserId(), userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        t.setAiMetaJson(json);
        repo.save(t); // do not create version for meta-only change
        return t.getAiMetaJson();
    }

    public Optional<String> aiMeta(Long templateId, Long userId) {
        return repo.findById(templateId)
            .filter(t -> Objects.equals(t.getUserId(), userId) || t.isShared())
            .map(EmailTemplate::getAiMetaJson);
    }

    @Transactional
    public void deleteTemplate(Long id, Long userId) {
        EmailTemplate t = repo.findById(id).orElseThrow();
        if (!Objects.equals(t.getUserId(), userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        // Delete child records first
        List<TemplateVersion> versions = versionRepo.findByTemplateIdOrderByVersionNumberDesc(id);
        if (!versions.isEmpty()) versionRepo.deleteAll(versions);
        List<TemplateVariable> vars = variableRepo.findByTemplateId(id);
        if (!vars.isEmpty()) variableRepo.deleteAll(vars);
        List<TemplateAsset> assets = assetRepo.findByTemplateId(id);
        if (!assets.isEmpty()) {
            // Remove objects from OCI, if used
            if (ociStorage != null && ociStorage.isEnabled()) {
                for (TemplateAsset a : assets) {
                    String key = a.getStorageKey();
                    if (key != null && !key.isBlank() && !key.startsWith("local://")) {
                        try { ociStorage.deleteObject(key); } catch (Exception ignore) {}
                    }
                }
            }
            assetRepo.deleteAll(assets);
        }
        // Finally delete template
        repo.delete(t);
    }

    @Transactional
    public EmailTemplate save(EmailTemplate t)   {
        boolean isNew = t.getId() == null;
        // If mjmlSource present, render before saving html snapshot
        if (t.getMjmlSource() != null && !t.getMjmlSource().isBlank()) {
            String rendered = mjmlRenderService.renderMjml(t.getMjmlSource());
            t.setLastRenderedHtml(rendered);
            
            // If html not manually set, use rendered output
            // if (t.getHtml() == null || t.getHtml().isBlank())
                t.setHtml(rendered);
        }
        EmailTemplate saved = repo.save(t);
        createVersionIfNeeded(saved, isNew);
        if (isNew) {
            ensureDefaultVariables(saved.getId());
        }
        return saved;
    }

    private void createVersionIfNeeded(EmailTemplate t, boolean force) {
        Optional<TemplateVersion> latest = versionRepo.findFirstByTemplateIdOrderByVersionNumberDesc(t.getId());
        if (force || latest.isEmpty() || !Objects.equals(latest.get().getHtml(), t.getHtml())) {
            TemplateVersion v = new TemplateVersion();
            v.setTemplateId(t.getId());
            v.setHtml(t.getHtml());
            v.setVersionNumber(latest.map(x -> x.getVersionNumber() + 1).orElse(1L));
            versionRepo.save(v);
        }
    }

    @Transactional
    public EmailTemplate duplicate(Long templateId, Long userId) {
        EmailTemplate orig = repo.findById(templateId).orElseThrow();
        EmailTemplate copy = new EmailTemplate();
        copy.setUserId(userId);
        copy.setName(orig.getName() + " Copy");
        copy.setHtml(orig.getHtml());
        copy.setCategoryId(orig.getCategoryId());
        copy.setDescription(orig.getDescription());
        copy.setTags(orig.getTags());
        copy.setBaseTemplateId(orig.getId());
        copy.setEasyEmailDesignJson(orig.getEasyEmailDesignJson());
        try {
            return save(copy);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return copy;
    }

    public String preview(EmailTemplate t, Map<String, Object> data) {
        return renderer.render(t, data);
    }

    public List<TemplateVersion> versions(Long templateId) {
        return versionRepo.findByTemplateIdOrderByVersionNumberDesc(templateId);
    }

    public List<TemplateVariable> variables(Long templateId) {
        return variableRepo.findByTemplateId(templateId);
    }

    @Transactional
    public void deleteVariable(Long templateId, Long variableId) {
        TemplateVariable v = variableRepo.findById(variableId).orElseThrow();
        if (!Objects.equals(v.getTemplateId(), templateId))
            throw new IllegalArgumentException("Variable mismatch");
        variableRepo.delete(v);
    }

    @Transactional
    public TemplateVariable registerVariable(Long templateId, String name, String defVal, boolean required) {
        TemplateVariable v = new TemplateVariable();
        v.setTemplateId(templateId);
        v.setVarName(name);
        v.setDefaultValue(defVal);
        v.setRequired(required);
        return variableRepo.save(v);
    }

    @Transactional
    public TemplateVariable updateVariable(Long templateId, Long variableId, String defaultValue, Boolean required) {
        TemplateVariable v = variableRepo.findById(variableId).orElseThrow();
        if (!Objects.equals(v.getTemplateId(), templateId))
            throw new IllegalArgumentException("Variable mismatch");
        if (defaultValue != null) v.setDefaultValue(defaultValue);
        if (required != null) v.setRequired(required);
        return variableRepo.save(v);
    }

    public String validateHtml(String html) {
        String cleaned = Jsoup.clean(html, Safelist.relaxed().addAttributes(":all", "style", "class", "id"));
        if (cleaned.length() > 200_000)
            throw new IllegalArgumentException("Template too large");
        return cleaned;
    }

    public String testSendPlaceholder() {
        return "Test send triggered (stub)";
    }

    // Categories
    public List<TemplateCategory> categories(Long userId) {
        return categoryRepo.findByUserId(userId);
    }

    @Transactional
    public TemplateCategory createCategory(Long userId, String name) {
        TemplateCategory c = new TemplateCategory();
        c.setUserId(userId);
        c.setName(name);
        c.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        return categoryRepo.save(c);
    }

    // Asset upload: supports optional folder prefix; when OCI is enabled, upload
    // bytes and store object key
    @Transactional
    public TemplateAsset addAsset(Long templateId, MultipartFile file) {
        return addAssetToFolder(templateId, null, file);
    }

    @Transactional
    public TemplateAsset addAssetToFolder(Long templateId, String folder, MultipartFile file) {
        TemplateAsset a = new TemplateAsset();
        a.setTemplateId(templateId);
        String origName = file.getOriginalFilename();
        String contentType = file.getContentType();
        a.setFileName(origName != null ? origName : "file");
        a.setContentType(contentType != null ? contentType : "application/octet-stream");
        a.setSizeBytes(file.getSize());

        String safeName = (origName == null ? "file" : origName).replaceAll("[^a-zA-Z0-9._-]", "-");
        String key = String.format("templates/%d/%s/%s-%s", templateId,
                (folder == null || folder.isBlank() ? "misc" : folder), UUID.randomUUID(), safeName);
        try {
            if (ociStorage != null && ociStorage.isEnabled()) {
                byte[] bytes = file.getBytes();
                ociStorage.putBytes(key, bytes, file.getContentType());
                a.setStorageKey(key);
                // Pre-generate long-lived read URL (default 30 days) and cache in DB
                try {
                    String url = ociStorage.generateReadUrl(key, defaultAssetReadExpiryMinutes);
                    a.setCachedReadUrl(url);
                    a.setCachedReadExpiresAt(LocalDateTime.now().plusMinutes(defaultAssetReadExpiryMinutes));
                } catch (Exception ignored) { }
            } else {
                // Fallback placeholder when no object storage configured
                a.setStorageKey("local://" + key);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to store asset", e);
        }
        return assetRepo.save(a);
    }

    public List<TemplateAsset> assets(Long templateId) {
        return assetRepo.findByTemplateId(templateId);
    }

    /**
     * Generate a temporary read URL for the given asset if backed by OCI storage.
     * Returns Optional.empty() if storage is not enabled or asset not found/owned.
     */
    public Optional<String> generateAssetReadUrl(Long templateId, Long assetId, int expiryMinutes) {
        Optional<TemplateAsset> opt = assetRepo.findById(assetId);
        if (opt.isEmpty()) return Optional.empty();
        TemplateAsset asset = opt.get();
        if (!Objects.equals(asset.getTemplateId(), templateId)) return Optional.empty();
        String key = asset.getStorageKey();
        if (key == null || key.isBlank()) return Optional.empty();
        // Local storage or disabled: no signed URL available
        if (ociStorage == null || !ociStorage.isEnabled() || key.startsWith("local://")) {
            return Optional.empty();
        }
        // Prefer cached URL if still valid; regenerate otherwise using default (30 days)
        LocalDateTime now = LocalDateTime.now();
        if (asset.getCachedReadUrl() != null && asset.getCachedReadExpiresAt() != null) {
            // Grace window of 60 seconds to avoid edge expiry at client
            if (asset.getCachedReadExpiresAt().isAfter(now.plusSeconds(60))) {
                return Optional.of(asset.getCachedReadUrl());
            }
        }
        int minutes = defaultAssetReadExpiryMinutes; // ignore requested shorter expiries; we want stable cache
        try {
            String newUrl = ociStorage.generateReadUrl(key, minutes);
            asset.setCachedReadUrl(newUrl);
            asset.setCachedReadExpiresAt(now.plusMinutes(minutes));
            assetRepo.save(asset);
            return Optional.of(newUrl);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Ensure core default merge variables exist for a newly created template so the
     * frontend does not need to inject them client-side. Idempotent: skips names that already exist.
     */
    private void ensureDefaultVariables(Long templateId) {
        List<String> defaults = List.of("first_name", "last_name", "company_name", "unsubscribe_url");
        List<TemplateVariable> existing = variableRepo.findByTemplateId(templateId);
        Set<String> existingNames = new HashSet<>();
        for (TemplateVariable v : existing) existingNames.add(v.getVarName());
        for (String name : defaults) {
            if (!existingNames.contains(name)) {
                TemplateVariable v = new TemplateVariable();
                v.setTemplateId(templateId);
                v.setVarName(name);
                // No defaultValue; required=false by default
                v.setRequired(false);
                v.setSystem(true);
                variableRepo.save(v);
            }
        }
    }
}
