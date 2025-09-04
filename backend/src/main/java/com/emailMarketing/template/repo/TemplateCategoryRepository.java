package com.emailMarketing.template.repo;
import com.emailMarketing.template.model.TemplateCategory; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; public interface TemplateCategoryRepository extends JpaRepository<TemplateCategory, Long>{ List<TemplateCategory> findByUserId(Long userId);} 
