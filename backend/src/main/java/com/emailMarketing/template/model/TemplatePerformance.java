package com.emailMarketing.template.model;

import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="template_performance")
public class TemplatePerformance { @Id private Long templateId; private Long totalSends=0L; private Long totalOpens=0L; private Long totalClicks=0L; private LocalDateTime lastUsedAt; public Long getTemplateId(){return templateId;} public void setTemplateId(Long templateId){this.templateId=templateId;} public Long getTotalSends(){return totalSends;} public void setTotalSends(Long totalSends){this.totalSends=totalSends;} public Long getTotalOpens(){return totalOpens;} public void setTotalOpens(Long totalOpens){this.totalOpens=totalOpens;} public Long getTotalClicks(){return totalClicks;} public void setTotalClicks(Long totalClicks){this.totalClicks=totalClicks;} public LocalDateTime getLastUsedAt(){return lastUsedAt;} public void setLastUsedAt(LocalDateTime lastUsedAt){this.lastUsedAt=lastUsedAt;} }
