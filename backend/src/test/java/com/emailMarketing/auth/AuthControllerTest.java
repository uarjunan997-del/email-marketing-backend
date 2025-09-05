package com.emailMarketing.auth;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.emailMarketing.config.JwtUtil;
import com.emailMarketing.subscription.User;
import com.emailMarketing.subscription.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.http.MediaType;

import java.util.Optional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserRepository userRepository;    
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private CustomUserDetailsService uds;

    @Test
    void register_ok() throws Exception {
        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        Mockito.when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
        Mockito.when(passwordEncoder.encode("pwlong")).thenReturn("ENC");
    Mockito.when(jwtUtil.generateAccessToken("alice", java.util.Set.of("USER"))).thenReturn("TOKEN");
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv->inv.getArgument(0));
    mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\",\"password\":\"pwlong\",\"email\":\"a@b.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("TOKEN"));
    }

    @Test
    void login_invalid() throws Exception {
        Mockito.when(authenticationManager.authenticate(Mockito.any()))
            .thenThrow(new org.springframework.security.authentication.BadCredentialsException("bad"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"u\",\"password\":\"p\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_ok() throws Exception {
    Authentication auth = new UsernamePasswordAuthenticationToken("alice", "pwlong");
        Mockito.when(authenticationManager.authenticate(Mockito.any())).thenReturn(auth);
    Mockito.when(jwtUtil.generateAccessToken("alice", java.util.Set.of("USER"))).thenReturn("TOKEN");
    com.emailMarketing.subscription.User user = new com.emailMarketing.subscription.User();
    user.setId(1L); user.setUsername("alice"); user.setEmail("a@b.com"); user.getRoles().add("USER");
    Mockito.when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\",\"password\":\"pwlong\"}"))
            .andExpect(status().isOk());
    }
}
