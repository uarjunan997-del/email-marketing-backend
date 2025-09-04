package com.emailMarketing.template.repo;
import com.emailMarketing.template.model.TemplateVersion; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.Optional;
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> { List<TemplateVersion> findByTemplateIdOrderByVersionNumberDesc(Long templateId); Optional<TemplateVersion> findFirstByTemplateIdOrderByVersionNumberDesc(Long templateId); }
