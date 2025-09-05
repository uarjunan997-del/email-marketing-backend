package com.emailMarketing.attribution.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emailMarketing.attribution.AttributionModel;

public interface AttributionModelRepository extends JpaRepository<AttributionModel, Long> {
    AttributionModel findByModelCode(String modelCode);
}
