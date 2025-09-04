package com.emailMarketing.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.emailMarketing.subscription.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Map;
import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AdvancedAuthController {
  private final AuthService authService; private final UserRepository userRepository;
  public AdvancedAuthController(AuthService authService, UserRepository userRepository){this.authService=authService; this.userRepository=userRepository;}

  public record RegisterRequest(@NotBlank String username, @NotBlank String password, @Email String email){}
  public record LoginRequest(@NotBlank String username, @NotBlank String password){}
  public record TokenResponse(String accessToken, String refreshToken, long expiresAt){ }
  public record RefreshRequest(@NotBlank String refreshToken){}
  public record ForgotPasswordRequest(@Email String email){}
  public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword){}

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest req){
    var u = authService.register(req.username(), req.email(), req.password());
    return ResponseEntity.ok(Map.of("userId", u.getId(), "message","verification_sent"));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request){
    String ip = request.getRemoteAddr();
    var access = authService.login(req.username(), req.password(), ip);
    var user = userRepository.findByUsername(req.username()).orElseThrow();
    var refresh = authService.createRefreshToken(user.getId());
    return ResponseEntity.ok(new TokenResponse(access, refresh, Instant.now().plusSeconds(900).toEpochMilli()));
  }

  @PostMapping("/refresh-token")
  public ResponseEntity<?> refresh(@RequestBody RefreshRequest req){
    // rotate stored refresh token and issue new access token
    // Here refresh token stored in DB separate from JWT access
    var newDbRefresh = authService.rotateRefresh(req.refreshToken());
    // find username by old refresh
    // Simplification: need userId from revoked token; service returns new token only; production would return both
    return ResponseEntity.ok(Map.of("refreshToken", newDbRefresh));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(@AuthenticationPrincipal UserDetails principal){
    var user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
    authService.logoutAll(user.getId());
    return ResponseEntity.ok(Map.of("status","ok"));
  }

  @GetMapping("/verify-email/{token}")
  public ResponseEntity<?> verify(@PathVariable String token){ authService.verifyEmail(token); return ResponseEntity.ok(Map.of("status","verified")); }

  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgot(@RequestBody ForgotPasswordRequest req){ authService.forgotPassword(req.email()); return ResponseEntity.ok(Map.of("status","sent")); }

  @PostMapping("/reset-password")
  public ResponseEntity<?> reset(@RequestBody ResetPasswordRequest req){ authService.resetPassword(req.token(), req.newPassword()); return ResponseEntity.ok(Map.of("status","reset")); }
}