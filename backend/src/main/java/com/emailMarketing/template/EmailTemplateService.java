package com.emailMarketing.template;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.emailMarketing.template.repo.*;
import com.emailMarketing.template.model.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EmailTemplateService {
    private final EmailTemplateRepository repo; private final TemplateVersionRepository versionRepo; private final TemplateVariableRepository variableRepo; private final TemplateRenderingService renderer; private final TemplateCategoryRepository categoryRepo; private final TemplateAssetRepository assetRepo; private final MjmlRenderService mjmlRenderService;
    public EmailTemplateService(EmailTemplateRepository repo, TemplateVersionRepository versionRepo, TemplateVariableRepository variableRepo, TemplateRenderingService renderer, TemplateCategoryRepository categoryRepo, TemplateAssetRepository assetRepo, MjmlRenderService mjmlRenderService){this.repo=repo; this.versionRepo=versionRepo; this.variableRepo=variableRepo; this.renderer=renderer; this.categoryRepo=categoryRepo; this.assetRepo=assetRepo; this.mjmlRenderService=mjmlRenderService;}
    public List<EmailTemplate> list(Long userId){ return repo.findByUserId(userId);}    
    public List<EmailTemplate> listAccessible(Long userId){ return repo.findAccessibleForUser(userId);}    
    public List<EmailTemplate> listShared(){ return repo.findByIsSharedTrue(); }
    public Optional<EmailTemplate> get(Long id){ return repo.findById(id);}   
    @Transactional public EmailTemplate save(EmailTemplate t){
        boolean isNew = t.getId()==null;
        // If mjmlSource present, render before saving html snapshot
        if(t.getMjmlSource()!=null && !t.getMjmlSource().isBlank()){
            String rendered = mjmlRenderService.renderMjml(t.getMjmlSource());
            t.setLastRenderedHtml(rendered);
            // If html not manually set, use rendered output
            if(t.getHtml()==null || t.getHtml().isBlank()) t.setHtml(rendered);
        }
        EmailTemplate saved = repo.save(t); createVersionIfNeeded(saved, isNew); return saved; }
    private void createVersionIfNeeded(EmailTemplate t, boolean force){ Optional<TemplateVersion> latest = versionRepo.findFirstByTemplateIdOrderByVersionNumberDesc(t.getId()); if(force || latest.isEmpty() || !Objects.equals(latest.get().getHtml(), t.getHtml())){ TemplateVersion v = new TemplateVersion(); v.setTemplateId(t.getId()); v.setHtml(t.getHtml()); v.setVersionNumber(latest.map(x->x.getVersionNumber()+1).orElse(1L)); versionRepo.save(v);} }
    @Transactional public EmailTemplate duplicate(Long templateId, Long userId){ EmailTemplate orig = repo.findById(templateId).orElseThrow(); EmailTemplate copy = new EmailTemplate(); copy.setUserId(userId); copy.setName(orig.getName()+" Copy"); copy.setHtml(orig.getHtml()); copy.setCategoryId(orig.getCategoryId()); copy.setDescription(orig.getDescription()); copy.setTags(orig.getTags()); copy.setBaseTemplateId(orig.getId()); return save(copy);}    
    public String preview(EmailTemplate t, Map<String,Object> data){ return renderer.render(t, data); }
    public List<TemplateVersion> versions(Long templateId){ return versionRepo.findByTemplateIdOrderByVersionNumberDesc(templateId);} 
    public List<TemplateVariable> variables(Long templateId){ return variableRepo.findByTemplateId(templateId);} 
    @Transactional public void deleteVariable(Long templateId, Long variableId){ TemplateVariable v = variableRepo.findById(variableId).orElseThrow(); if(!Objects.equals(v.getTemplateId(), templateId)) throw new IllegalArgumentException("Variable mismatch"); variableRepo.delete(v);}   
    @Transactional public TemplateVariable registerVariable(Long templateId, String name, String defVal, boolean required){ TemplateVariable v = new TemplateVariable(); v.setTemplateId(templateId); v.setVarName(name); v.setDefaultValue(defVal); v.setRequired(required); return variableRepo.save(v);}   
    public String validateHtml(String html){ String cleaned = Jsoup.clean(html, Safelist.relaxed().addAttributes(":all","style","class","id")); if(cleaned.length()>200_000) throw new IllegalArgumentException("Template too large"); return cleaned; }
    public String testSendPlaceholder(){ return "Test send triggered (stub)"; }

    // Categories
    public List<TemplateCategory> categories(Long userId){ return categoryRepo.findByUserId(userId);}    
    @Transactional public TemplateCategory createCategory(Long userId, String name){ TemplateCategory c = new TemplateCategory(); c.setUserId(userId); c.setName(name); c.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+","-")); return categoryRepo.save(c);}   

    // Asset upload stub (storage integration placeholder)
    @Transactional public TemplateAsset addAsset(Long templateId, MultipartFile file){ TemplateAsset a = new TemplateAsset(); a.setTemplateId(templateId); a.setFileName(file.getOriginalFilename()); a.setContentType(file.getContentType()); a.setSizeBytes(file.getSize()); a.setStorageKey("local://"+UUID.randomUUID()); return assetRepo.save(a);}    
    public List<TemplateAsset> assets(Long templateId){ return assetRepo.findByTemplateId(templateId);}  
}
