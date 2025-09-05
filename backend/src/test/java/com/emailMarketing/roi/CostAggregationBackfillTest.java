package com.emailMarketing.roi;

import org.junit.jupiter.api.Test; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.test.context.ActiveProfiles; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.transaction.annotation.Transactional; import java.time.*; import org.springframework.jdbc.core.JdbcTemplate; import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CostAggregationBackfillTest {
    @Autowired CostAggregationService service; @Autowired JdbcTemplate jdbc;

    @Test void backfillInsertsRows(){
        // seed a cost on each of 3 days
        for(int i=0;i<3;i++){
            jdbc.update("INSERT INTO CAMPAIGN_COSTS(CAMPAIGN_ID,CATEGORY_CODE,COST_DATE,AMOUNT,CURRENCY,ORG_ID) VALUES(?,?,?,?,?,?)", 99L, "PLATFORM", java.sql.Date.valueOf(LocalDate.now().minusDays(i)), 50.0, "USD", 7L);
        }
        var start = LocalDate.now().minusDays(2); var end= LocalDate.now();
        var res = service.backfill(start,end);
        assertThat(res.get("status")).isEqualTo("OK");
        Integer rows = (Integer)res.get("rows"); assertThat(rows).isGreaterThan(0);
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM COST_DAILY_AGG WHERE ORG_ID=7 AND COST_DATE BETWEEN ? AND ?", Integer.class, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
        assertThat(count).isEqualTo(3);
    }
}
