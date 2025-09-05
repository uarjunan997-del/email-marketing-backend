package com.emailMarketing.roi;

import org.junit.jupiter.api.*; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.beans.factory.annotation.Autowired; import java.time.*;

@SpringBootTest @org.springframework.test.context.ActiveProfiles("test")
class CostAggregationServiceTest {
    @Autowired CostAggregationService svc;

    @Test void aggregateDay_noData_ok() {
        var res = svc.aggregateDay(LocalDate.now().minusDays(1));
        Assertions.assertEquals("OK", res.get("status"));
    }
}
