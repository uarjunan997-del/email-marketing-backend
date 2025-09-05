package com.emailMarketing.roi.repo; import com.emailMarketing.roi.CostCategory; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CostCategoryRepository extends JpaRepository<CostCategory, Long> { Optional<CostCategory> findByCode(String code); List<CostCategory> findByCategoryType(String categoryType); }
