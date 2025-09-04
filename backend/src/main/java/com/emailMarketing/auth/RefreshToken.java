package com.emailMarketing.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="refresh_tokens", indexes=@Index(name="idx_rt_user", columnList="user_id"))
public class RefreshToken {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id", nullable=false) private Long userId;
  @Column(nullable=false, unique=true, length=128) private String token;
  @Column(nullable=false) private Instant expiry;
  private boolean revoked = false;
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
  public String getToken(){return token;} public void setToken(String token){this.token=token;}
  public Instant getExpiry(){return expiry;} public void setExpiry(Instant expiry){this.expiry=expiry;}
  public boolean isRevoked(){return revoked;} public void setRevoked(boolean r){this.revoked=r;}
}
