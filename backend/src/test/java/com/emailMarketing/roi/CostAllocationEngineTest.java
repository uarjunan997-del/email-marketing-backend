package com.emailMarketing.roi;

import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.transaction.annotation.Transactional; import org.springframework.test.context.ActiveProfiles; import java.time.*; import org.springframework.jdbc.core.JdbcTemplate; import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CostAllocationEngineTest {
    @Autowired JdbcTemplate jdbc; @Autowired com.emailMarketing.roi.repo.CostAllocationRepository allocRepo; @Autowired com.emailMarketing.roi.repo.CostAllocationLinkRepository linkRepo; @Autowired CostAggregationService service;

    @Test void allocationDistributesDailyPortion(){
        // seed allocation spanning 2 days with total cost 200 USD and two links weights 1:3
        CostAllocation alloc = new CostAllocation(); alloc.setResourceName("Shared Tool"); alloc.setAllocationMethod("PERCENTAGE"); alloc.setBasisValue(1.0); alloc.setTotalCost(200.0); alloc.setCurrency("USD"); alloc.setPeriodStart(LocalDate.now().minusDays(1)); alloc.setPeriodEnd(LocalDate.now()); alloc.setOrgId(5L); allocRepo.save(alloc);
        CostAllocationLink l1 = new CostAllocationLink(); CostAllocationLink.LinkId id1 = new CostAllocationLink.LinkId(); id1.setAllocationId(alloc.getId()); id1.setCampaignId(10L); l1.setId(id1); l1.setWeightValue(1.0); linkRepo.save(l1);
        CostAllocationLink l2 = new CostAllocationLink(); CostAllocationLink.LinkId id2 = new CostAllocationLink.LinkId(); id2.setAllocationId(alloc.getId()); id2.setCampaignId(11L); l2.setId(id2); l2.setWeightValue(3.0); linkRepo.save(l2);

        // run for each day
        var day1 = LocalDate.now().minusDays(1); var day2 = LocalDate.now();
        service.aggregateDay(day1); service.aggregateDay(day2);
        Double d1 = jdbc.queryForObject("SELECT TOTAL_COST FROM COST_DAILY_AGG WHERE ORG_ID=5 AND COST_DATE=?", Double.class, java.sql.Date.valueOf(day1));
        Double d2 = jdbc.queryForObject("SELECT TOTAL_COST FROM COST_DAILY_AGG WHERE ORG_ID=5 AND COST_DATE=?", Double.class, java.sql.Date.valueOf(day2));
        // Each day receives half of total (100) then distributed across org totals (org aggregation merges here); we only store org total so expect 100 per day
        assertThat(d1).isNotNull(); assertThat(d2).isNotNull(); assertThat(d1).isEqualTo(100.0); assertThat(d2).isEqualTo(100.0);
    }
}
