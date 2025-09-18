package com.emailMarketing.template.repo;

import com.emailMarketing.template.model.TemplateVariableBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TemplateVariableBindingRepository extends JpaRepository<TemplateVariableBinding, Long> {
    List<TemplateVariableBinding> findByTemplateId(Long templateId);
    Optional<TemplateVariableBinding> findByTemplateIdAndVarName(Long templateId, String varName);
    void deleteByTemplateIdAndId(Long templateId, Long id);
}
