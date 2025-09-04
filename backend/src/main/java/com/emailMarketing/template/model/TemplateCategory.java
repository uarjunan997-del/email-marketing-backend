package com.emailMarketing.template.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="template_categories", indexes=@Index(name="idx_category_user", columnList="user_id"))
public class TemplateCategory {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; private Long userId; @Column(nullable=false) private String name; @Column(nullable=false) private String slug; private LocalDateTime createdAt = LocalDateTime.now();
  public Long getId(){return id;} public void setId(Long id){this.id=id;} public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;} public String getName(){return name;} public void setName(String name){this.name=name;} public String getSlug(){return slug;} public void setSlug(String slug){this.slug=slug;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
