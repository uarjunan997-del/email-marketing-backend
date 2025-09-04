package com.emailMarketing.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailMarketing.auth.EmailVerificationToken;

import java.util.Optional;
import java.time.Instant;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
  Optional<EmailVerificationToken> findByTokenAndUsedFalse(String token);
  void deleteByExpiryBefore(Instant ts);
}
