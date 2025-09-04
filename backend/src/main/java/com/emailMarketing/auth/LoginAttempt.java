package com.emailMarketing.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="login_attempts", indexes=@Index(name="idx_la_ip", columnList="ip"))
public class LoginAttempt {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false, length=64) private String ip;
  @Column(nullable=false) private Instant ts = Instant.now();
  @Column(nullable=false) private boolean success;
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public String getIp(){return ip;} public void setIp(String ip){this.ip=ip;}
  public Instant getTs(){return ts;} public void setTs(Instant ts){this.ts=ts;}
  public boolean isSuccess(){return success;} public void setSuccess(boolean success){this.success=success;}
}
