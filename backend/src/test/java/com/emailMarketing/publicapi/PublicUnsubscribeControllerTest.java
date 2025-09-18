package com.emailMarketing.publicapi;

import com.emailMarketing.deliverability.UnsubscribeTokenService;
import com.emailMarketing.deliverability.SuppressionRepository;
import com.emailMarketing.contact.ContactRepository;
import com.emailMarketing.contact.Contact;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicUnsubscribeControllerTest {
  @Test
  void verify_and_confirm_flow() throws Exception {
    UnsubscribeTokenService tokenService = Mockito.mock(UnsubscribeTokenService.class);
    ContactRepository contactRepo = Mockito.mock(ContactRepository.class);
    SuppressionRepository suppressionRepo = Mockito.mock(SuppressionRepository.class);
    String token = "tok";
    Mockito.when(tokenService.validate(token)).thenReturn(new UnsubscribeTokenService.Decoded(5L, "user@example.com", 9L));
    Contact c = new Contact(); c.setId(1L); c.setUserId(5L); c.setEmail("user@example.com");
    Mockito.when(contactRepo.findAll()).thenReturn(List.of(c));
    Mockito.when(suppressionRepo.existsByUserIdAndEmail(5L, "user@example.com")).thenReturn(false);
    PublicUnsubscribeController controller = new PublicUnsubscribeController(tokenService, contactRepo, suppressionRepo);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(get("/public/unsubscribe/verify").param("token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.email").value("user@example.com"));
    mvc.perform(post("/public/unsubscribe/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNSUBSCRIBED"));
  }
}
