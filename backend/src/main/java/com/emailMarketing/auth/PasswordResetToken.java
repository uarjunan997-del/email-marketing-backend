package com.emailMarketing.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="password_reset_tokens", indexes=@Index(name="idx_prt_user", columnList="user_id"))
public class PasswordResetToken {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id", nullable=false) private Long userId;
  @Column(nullable=false, unique=true, length=100) private String token;
  @Column(nullable=false) private Instant expiry;
  private boolean used = false;
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
  public String getToken(){return token;} public void setToken(String token){this.token=token;}
  public Instant getExpiry(){return expiry;} public void setExpiry(Instant expiry){this.expiry=expiry;}
  public boolean isUsed(){return used;} public void setUsed(boolean used){this.used=used;}
}
