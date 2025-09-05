package com.emailMarketing.timeseries;

import com.emailMarketing.subscription.UserRepository;
import com.emailMarketing.subscription.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import com.emailMarketing.timeseries.repo.AdminBackfillAuditRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimeSeriesAdminSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockBean AggregationService aggregationService; @MockBean com.emailMarketing.ratelimit.RateLimiterService rateLimiter; @MockBean AdminBackfillAuditRepository auditRepo; @MockBean UserRepository userRepository;

    @BeforeEach void users(){
        org.mockito.Mockito.when(userRepository.findByUsername("admin")).thenReturn(java.util.Optional.of(make("admin","ROLE_ADMIN")));
        org.mockito.Mockito.when(userRepository.findByUsername("plain")).thenReturn(java.util.Optional.of(make("plain","ROLE_USER")));
        org.mockito.Mockito.when(rateLimiter.allow(org.mockito.Mockito.anyString())).thenReturn(true);
    }
    private User make(String u,String role){ User x=new User(); x.setId("admin".equals(u)?2L:1L); x.setUsername(u); x.setEmail(u+"@e.com"); x.getRoles().add(role); return x; }

    @Test @WithMockUser(username="plain", authorities={"ROLE_USER"})
    void userCannotBackfill(){
        try{ mockMvc.perform(post("/api/timeseries/admin/aggregate/backfill")
            .param("userId","1").param("start",java.time.Instant.now().minusSeconds(3600).toString()).param("end", java.time.Instant.now().toString())
            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden()); } catch(Exception e){ throw new RuntimeException(e); }
    }

    @Test @WithMockUser(username="admin", authorities={"ROLE_ADMIN"})
    void adminCanViewAudit(){
        try{
            when(auditRepo.findTop50ByOrderByTriggeredAtDesc()).thenReturn(java.util.List.of());
            mockMvc.perform(get("/api/timeseries/admin/aggregate/audit"))
                .andExpect(status().isOk());
            verify(auditRepo, times(1)).findTop50ByOrderByTriggeredAtDesc();
        } catch(Exception e){ throw new RuntimeException(e); }
    }

    @Test @WithMockUser(username="admin", authorities={"ROLE_ADMIN"})
    void adminBackfillCreatesAuditRecord(){
        try{
            var entity = new AdminBackfillAudit();
            java.lang.reflect.Field f = AdminBackfillAudit.class.getDeclaredField("id"); f.setAccessible(true); f.set(entity, 42L);
            when(auditRepo.save(any())).thenReturn(entity);
            when(rateLimiter.allow(anyString())).thenReturn(true);
            doNothing().when(aggregationService).manualBackfill(anyLong(), any(), any());
            var start = java.time.Instant.now().minusSeconds(3600).toString();
            var end = java.time.Instant.now().toString();
            mockMvc.perform(post("/api/timeseries/admin/aggregate/backfill")
                .param("userId","1")
                .param("start", start)
                .param("end", end)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
            verify(auditRepo, times(1)).save(any());
            verify(aggregationService, times(1)).manualBackfill(anyLong(), any(), any());
        }catch(Exception e){ throw new RuntimeException(e); }
    }
    // Using application SecurityConfig imported above; no local override needed
}