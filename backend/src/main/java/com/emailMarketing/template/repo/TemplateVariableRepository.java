package com.emailMarketing.template.repo;
import com.emailMarketing.template.model.TemplateVariable; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; public interface TemplateVariableRepository extends JpaRepository<TemplateVariable, Long>{ List<TemplateVariable> findByTemplateId(Long templateId);} 
