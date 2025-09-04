package com.emailMarketing.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailMarketing.auth.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  Optional<RefreshToken> findByTokenAndRevokedFalse(String token);
  void deleteByUserId(Long userId);
}
