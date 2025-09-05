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
    CampaignABTestRepository abRepo = Mockito.mock(CampaignABTestRepository.class);
    CampaignRecipientRepository recipientRepo = Mockito.mock(CampaignRecipientRepository.class);
    SendingQueueRepository queueRepo = Mockito.mock(SendingQueueRepository.class);
    CampaignScheduleRepository scheduleRepo = Mockito.mock(CampaignScheduleRepository.class);
    com.emailMarketing.template.EmailTemplateService templateService = Mockito.mock(com.emailMarketing.template.EmailTemplateService.class);
    com.emailMarketing.subscription.SubscriptionRepository subscriptionRepository = Mockito.mock(com.emailMarketing.subscription.SubscriptionRepository.class);
    com.emailMarketing.deliverability.EmailBounceRepository bounceRepo = Mockito.mock(com.emailMarketing.deliverability.EmailBounceRepository.class);
    com.emailMarketing.deliverability.EmailComplaintRepository complaintRepo = Mockito.mock(com.emailMarketing.deliverability.EmailComplaintRepository.class);
    com.emailMarketing.deliverability.SuppressionRepository suppressionRepo = Mockito.mock(com.emailMarketing.deliverability.SuppressionRepository.class);
    com.emailMarketing.deliverability.UnsubscribeTokenService unsubscribeTokenService = Mockito.mock(com.emailMarketing.deliverability.UnsubscribeTokenService.class);
    CampaignRoiRepository roiRepo = Mockito.mock(CampaignRoiRepository.class);
    CampaignService service = new CampaignService(campaignRepo, contactRepo, abRepo, recipientRepo, queueRepo, scheduleRepo, templateService, subscriptionRepository, bounceRepo, complaintRepo, suppressionRepo, unsubscribeTokenService, roiRepo);
        Mockito.when(contactRepo.countByUserIdAndUnsubscribedFalse(1L)).thenReturn(5L);
        Mockito.when(campaignRepo.save(Mockito.any())).thenAnswer(inv->inv.getArgument(0));
        Campaign c = new Campaign(); c.setUserId(1L); c.setName("Test");
        Campaign saved = service.create(c);
        assertThat(saved.getTotalRecipients()).isEqualTo(5);
    }
}
