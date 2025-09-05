package com.emailMarketing.deliverability;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="suppression_list", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","email"}), indexes = {@Index(name="idx_suppress_user_email", columnList="user_id,email")})
public class SuppressionEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(nullable=false, length=320) private String email;
    private String reason; private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
    public String getReason(){return reason;} public void setReason(String reason){this.reason=reason;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
