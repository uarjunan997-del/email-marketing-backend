package com.emailMarketing.attribution.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.attribution.AttributionFraudFlag;

public interface AttributionFraudFlagRepository extends JpaRepository<AttributionFraudFlag, Long> { }
