package com.emailMarketing.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailMarketing.auth.repository.*;
import com.emailMarketing.config.JwtUtil;
import com.emailMarketing.subscription.User;
import com.emailMarketing.subscription.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Service
public class AuthService {
  private final UserRepository userRepository; private final RefreshTokenRepository refreshRepo; private final EmailVerificationTokenRepository emailVerRepo; private final PasswordResetTokenRepository prRepo; private final LoginAttemptRepository laRepo; private final JwtUtil jwt; private final PasswordEncoder encoder; private final JavaMailSender mailSender; private final com.emailMarketing.security.InMemoryRateLimiter rateLimiter;
  public AuthService(UserRepository userRepository, RefreshTokenRepository refreshRepo, EmailVerificationTokenRepository emailVerRepo, PasswordResetTokenRepository prRepo, LoginAttemptRepository laRepo, JwtUtil jwt, PasswordEncoder encoder, JavaMailSender mailSender, com.emailMarketing.security.InMemoryRateLimiter rateLimiter){this.userRepository=userRepository; this.refreshRepo=refreshRepo; this.emailVerRepo=emailVerRepo; this.prRepo=prRepo; this.laRepo=laRepo; this.jwt=jwt; this.encoder=encoder; this.mailSender=mailSender; this.rateLimiter=rateLimiter;}

  @Transactional
  public User register(String username, String email, String rawPassword){
    if(userRepository.findByUsername(username).isPresent()) throw new IllegalArgumentException("Username exists");
    if(userRepository.findByEmail(email).isPresent()) throw new IllegalArgumentException("Email exists");
    User u = new User(); u.setUsername(username); u.setEmail(email); u.setPassword(encoder.encode(rawPassword)); u.getRoles().add("USER");
    userRepository.save(u);
    var evt = new EmailVerificationToken(); evt.setUserId(u.getId()); evt.setToken(token()); evt.setExpiry(Instant.now().plus(Duration.ofHours(24))); emailVerRepo.save(evt);
    send(email, "Verify your account", "Click link: https://app.example.com/verify?token="+evt.getToken());
    return u;
  }

  public String login(String username, String password, String ip){
    String key = "login:"+ip;
    if(!rateLimiter.isAllowed(key)) throw new IllegalStateException("RATE_LIMIT_EXCEEDED");
    var user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
    if(!encoder.matches(password, user.getPassword())){ recordAttempt(ip,false); throw new IllegalArgumentException("Invalid credentials"); }
    if(!user.isEmailVerified()) throw new IllegalStateException("EMAIL_NOT_VERIFIED");
    if(user.isLocked() || !user.isActive()) throw new IllegalStateException("USER_DISABLED");
    recordAttempt(ip,true);
    rateLimiter.reset(key);
    return jwt.generateAccessToken(user.getUsername(), user.getRoles());
  }

  @Transactional
  public String createRefreshToken(Long userId){
    RefreshToken rt = new RefreshToken(); rt.setUserId(userId); rt.setToken(token()); rt.setExpiry(Instant.now().plus(Duration.ofDays(7))); refreshRepo.save(rt); return rt.getToken();
  }

  @Transactional
  public String rotateRefresh(String refreshToken){
    var rt = refreshRepo.findByTokenAndRevokedFalse(refreshToken).orElseThrow(() -> new IllegalArgumentException("Invalid refresh"));
    if(rt.getExpiry().isBefore(Instant.now())) throw new IllegalArgumentException("Expired refresh");
    rt.setRevoked(true);
    return createRefreshToken(rt.getUserId());
  }

  @Transactional
  public void logoutAll(Long userId){ refreshRepo.deleteByUserId(userId); }

  @Transactional
  public void verifyEmail(String token){
    var evt = emailVerRepo.findByTokenAndUsedFalse(token).orElseThrow(() -> new IllegalArgumentException("Invalid token"));
    if(evt.getExpiry().isBefore(Instant.now())) throw new IllegalArgumentException("Expired token");
    var user = userRepository.findById(evt.getUserId()).orElseThrow();
    user.setEmailVerified(true); evt.setUsed(true);
  }

  @Transactional
  public void forgotPassword(String email){
    var user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Not found"));
    PasswordResetToken pr = new PasswordResetToken(); pr.setUserId(user.getId()); pr.setToken(token()); pr.setExpiry(Instant.now().plus(Duration.ofHours(2))); prRepo.save(pr);
    send(email, "Password reset", "Use token: "+pr.getToken());
  }

  @Transactional
  public void resetPassword(String token, String newPassword){
    var pr = prRepo.findByTokenAndUsedFalse(token).orElseThrow(() -> new IllegalArgumentException("Invalid token"));
    if(pr.getExpiry().isBefore(Instant.now())) throw new IllegalArgumentException("Expired token");
    var user = userRepository.findById(pr.getUserId()).orElseThrow();
    user.setPassword(encoder.encode(newPassword)); pr.setUsed(true);
  }

  private void recordAttempt(String ip, boolean success){
    LoginAttempt la = new LoginAttempt(); la.setIp(ip); la.setSuccess(success); laRepo.save(la);
  }

  private String token(){ return UUID.randomUUID().toString().replaceAll("-","" ); }
  private void send(String to, String subject, String body){ try { SimpleMailMessage m = new SimpleMailMessage(); m.setTo(to); m.setSubject(subject); m.setText(body); mailSender.send(m);} catch(Exception ignored){} }
}
