package com.emailMarketing.template.repo;
import com.emailMarketing.template.model.TemplateAsset; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; public interface TemplateAssetRepository extends JpaRepository<TemplateAsset, Long>{ List<TemplateAsset> findByTemplateId(Long templateId);} 
