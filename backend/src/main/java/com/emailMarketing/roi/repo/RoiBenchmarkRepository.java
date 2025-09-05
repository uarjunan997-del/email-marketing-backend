package com.emailMarketing.roi.repo; import com.emailMarketing.roi.RoiBenchmark; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RoiBenchmarkRepository extends JpaRepository<RoiBenchmark, RoiBenchmark.Pk> { List<RoiBenchmark> findByIdIndustryCode(String industryCode); }
