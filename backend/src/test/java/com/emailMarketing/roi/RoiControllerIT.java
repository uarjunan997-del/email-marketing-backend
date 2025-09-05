package com.emailMarketing.roi;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class RoiControllerIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper om;

    @Test @WithMockUser(username="tester", roles={"USER","ADMIN"})
    void addCostsAndFetchCampaignRoi_emptyRevenue_ok() throws Exception {
        var req = List.of(new RoiService.AddCostRequest("PLATFORM_EMAIL_SEND", null, LocalDate.now().minusDays(1), 100.0, "USD", 1000.0, 0.1, null, 1L));
        mvc.perform(post("/api/costs/campaign/10").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/roi/campaign/10")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OK"));
    }

    @Test @WithMockUser(username="tester", roles={"USER"})
    void benchmarksEndpoint_ok() throws Exception {
        mvc.perform(get("/api/roi/benchmarks")).andExpect(status().isOk()).andExpect(jsonPath("$.metrics").exists());
    }
}
