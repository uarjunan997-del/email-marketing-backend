package com.emailMarketing.dashboard;

import com.emailMarketing.subscription.User;
import com.emailMarketing.subscription.UserRepository;
import com.emailMarketing.config.JwtFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@ActiveProfiles("test")
class DashboardAdminSecurityIT {

    @Autowired MockMvc mockMvc;
    @MockBean DashboardService dashboardService;
    @MockBean UserRepository userRepository;
    @MockBean JwtFilter jwtFilter; // Added mocked JwtFilter bean
    // No JWT filter in slice test; simplified security chain defined below

    @BeforeEach void stubUsers(){
        org.mockito.Mockito.when(userRepository.findByUsername("plain")).thenReturn(java.util.Optional.of(make("plain","ROLE_USER")));
        org.mockito.Mockito.when(userRepository.findByUsername("admin")).thenReturn(java.util.Optional.of(make("admin","ROLE_ADMIN")));
    }
    private User make(String u, String role){ User x=new User(); x.setId("admin".equals(u)?2L:1L); x.setUsername(u); x.setEmail(u+"@e.com"); x.getRoles().add(role); return x; }

    @Test
    @WithMockUser(username="plain", authorities={"ROLE_USER"})
    void nonAdminCannotEvict() throws Exception {
        mockMvc.perform(post("/api/dashboard/admin/evict").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username="admin", authorities={"ROLE_ADMIN"})
    void adminCanEvict() throws Exception {
        mockMvc.perform(post("/api/dashboard/admin/evict").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        org.springframework.security.web.SecurityFilterChain testChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().authenticated());
            return http.build();
        }
    }
}
