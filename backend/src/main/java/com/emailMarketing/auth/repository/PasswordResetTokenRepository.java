package com.emailMarketing.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailMarketing.auth.PasswordResetToken;

import java.util.Optional;
import java.time.Instant;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
  Optional<PasswordResetToken> findByTokenAndUsedFalse(String token);
  void deleteByExpiryBefore(Instant ts);
}
