package com.emailMarketing.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailMarketing.auth.LoginAttempt;

import java.util.List;
import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
  List<LoginAttempt> findByIpAndTsAfter(String ip, Instant after);
}
