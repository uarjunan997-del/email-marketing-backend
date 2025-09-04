package com.emailMarketing.campaign;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


import com.emailMarketing.contact.ContactRepository;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignServiceTest {
    @Test
    void create_sets_recipient_count() {
        CampaignRepository campaignRepo = Mockito.mock(CampaignRepository.class);
        ContactRepository contactRepo = Mockito.mock(ContactRepository.class);
        CampaignService service = new CampaignService(campaignRepo, contactRepo);
        Mockito.when(contactRepo.countByUserIdAndUnsubscribedFalse(1L)).thenReturn(5L);
        Mockito.when(campaignRepo.save(Mockito.any())).thenAnswer(inv->inv.getArgument(0));
        Campaign c = new Campaign(); c.setUserId(1L); c.setName("Test");
        Campaign saved = service.create(c);
        assertThat(saved.getTotalRecipients()).isEqualTo(5);
    }
}
