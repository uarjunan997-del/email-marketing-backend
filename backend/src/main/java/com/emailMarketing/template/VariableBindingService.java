package com.emailMarketing.template;

import com.emailMarketing.template.model.TemplateVariableBinding;
import com.emailMarketing.template.repo.TemplateVariableBindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VariableBindingService {
    private final TemplateVariableBindingRepository repo;

    public VariableBindingService(TemplateVariableBindingRepository repo) {
        this.repo = repo;
    }

    public List<TemplateVariableBinding> list(Long templateId){
        return repo.findByTemplateId(templateId);
    }

    @Transactional
    public TemplateVariableBinding upsert(Long templateId, String varName, TemplateVariableBinding.SourceType sourceType,
                                          String sourceKey, String defaultValue, String transformJson){
        validateVarName(varName);
        var opt = repo.findByTemplateIdAndVarName(templateId, varName);
        TemplateVariableBinding b = opt.orElseGet(TemplateVariableBinding::new);
        b.setTemplateId(templateId);
        b.setVarName(varName);
        b.setSourceType(sourceType);
        b.setSourceKey(sourceKey);
        b.setDefaultValue(defaultValue);
        b.setTransformJson(transformJson);
        return repo.save(b);
    }

    @Transactional
    public void delete(Long templateId, Long bindingId){
        repo.deleteByTemplateIdAndId(templateId, bindingId);
    }

    private void validateVarName(String name){
        if(name == null || !name.matches("[a-zA-Z0-9_.]+")){
            throw new IllegalArgumentException("Invalid variable name: " + name);
        }
    }
}
