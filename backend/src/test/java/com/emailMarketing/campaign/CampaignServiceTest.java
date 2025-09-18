package com.emailMarketing.campaign;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    com.emailMarketing.template.TemplateRenderingService renderingService = Mockito.mock(com.emailMarketing.template.TemplateRenderingService.class);
    com.emailMarketing.deliverability.EmailBounceRepository bounceRepo = Mockito.mock(com.emailMarketing.deliverability.EmailBounceRepository.class);
    com.emailMarketing.deliverability.EmailComplaintRepository complaintRepo = Mockito.mock(com.emailMarketing.deliverability.EmailComplaintRepository.class);
    com.emailMarketing.deliverability.SuppressionRepository suppressionRepo = Mockito.mock(com.emailMarketing.deliverability.SuppressionRepository.class);
    com.emailMarketing.deliverability.UnsubscribeTokenService unsubscribeTokenService = Mockito.mock(com.emailMarketing.deliverability.UnsubscribeTokenService.class);
    CampaignRoiRepository roiRepo = Mockito.mock(CampaignRoiRepository.class);
    CampaignService service = new CampaignService(campaignRepo, contactRepo, abRepo, recipientRepo, queueRepo, scheduleRepo, templateService, renderingService, subscriptionRepository, bounceRepo, complaintRepo, suppressionRepo, unsubscribeTokenService, roiRepo);
        Mockito.when(contactRepo.countByUserIdAndUnsubscribedFalse(1L)).thenReturn(5L);
        Mockito.when(campaignRepo.save(Mockito.any())).thenAnswer(inv->inv.getArgument(0));
        Campaign c = new Campaign(); c.setUserId(1L); c.setName("Test");
        Campaign saved = service.create(c);
        assertThat(saved.getTotalRecipients()).isEqualTo(5);
    }

    @Test
    void enqueue_builds_rendered_body_with_renderer() {
    CampaignRepository campaignRepo = Mockito.mock(CampaignRepository.class);
    ContactRepository contactRepo = Mockito.mock(ContactRepository.class);
    CampaignABTestRepository abRepo = Mockito.mock(CampaignABTestRepository.class);
    CampaignRecipientRepository recipientRepo = Mockito.mock(CampaignRecipientRepository.class);
    SendingQueueRepository queueRepo = Mockito.mock(SendingQueueRepository.class);
    CampaignScheduleRepository scheduleRepo = Mockito.mock(CampaignScheduleRepository.class);
    com.emailMarketing.template.EmailTemplateService templateService = Mockito.mock(com.emailMarketing.template.EmailTemplateService.class);
    com.emailMarketing.template.TemplateRenderingService renderingService = Mockito.mock(com.emailMarketing.template.TemplateRenderingService.class);
    com.emailMarketing.subscription.SubscriptionRepository subscriptionRepository = Mockito.mock(com.emailMarketing.subscription.SubscriptionRepository.class);
    com.emailMarketing.deliverability.EmailBounceRepository bounceRepo = Mockito.mock(com.emailMarketing.deliverability.EmailBounceRepository.class);
    com.emailMarketing.deliverability.EmailComplaintRepository complaintRepo = Mockito.mock(com.emailMarketing.deliverability.EmailComplaintRepository.class);
    com.emailMarketing.deliverability.SuppressionRepository suppressionRepo = Mockito.mock(com.emailMarketing.deliverability.SuppressionRepository.class);
    com.emailMarketing.deliverability.UnsubscribeTokenService unsubscribeTokenService = Mockito.mock(com.emailMarketing.deliverability.UnsubscribeTokenService.class);
    CampaignRoiRepository roiRepo = Mockito.mock(CampaignRoiRepository.class);
    CampaignService service = new CampaignService(campaignRepo, contactRepo, abRepo, recipientRepo, queueRepo, scheduleRepo, templateService, renderingService, subscriptionRepository, bounceRepo, complaintRepo, suppressionRepo, unsubscribeTokenService, roiRepo);

    // Campaign with template
    Campaign c = new Campaign(); c.setId(10L); c.setUserId(1L); c.setTemplateId(55L); c.setStatus("DRAFT"); c.setTotalRecipients(1); c.setSubject("Hello");
    Mockito.when(campaignRepo.findById(10L)).thenReturn(java.util.Optional.of(c));
    Mockito.when(campaignRepo.save(Mockito.any())).thenAnswer(inv->inv.getArgument(0));
    // Contact
    com.emailMarketing.contact.Contact ct = new com.emailMarketing.contact.Contact(); ct.setId(7L); ct.setUserId(1L); ct.setEmail("a@example.com"); ct.setFirstName("Ada");
    Mockito.when(contactRepo.findByUserId(1L)).thenReturn(java.util.List.of(ct));
    Mockito.when(contactRepo.countByUserIdAndUnsubscribedFalse(1L)).thenReturn(1L);
    // Template fetch
    com.emailMarketing.template.EmailTemplate template = new com.emailMarketing.template.EmailTemplate(); template.setId(55L); template.setHtml("<p>Hi {{first_name}}</p>{{unsubscribe}}");
    Mockito.when(templateService.get(55L)).thenReturn(java.util.Optional.of(template));
    // Rendering service result
    com.emailMarketing.template.TemplateRenderingService.RenderResult rr = new com.emailMarketing.template.TemplateRenderingService.RenderResult("<p>Hi Ada</p><a href='u'>Unsubscribe</a>", java.util.Set.of(), java.util.Map.of());
    Mockito.when(renderingService.renderDetailed(Mockito.eq(template), Mockito.anyMap())).thenReturn(rr);
    Mockito.when(unsubscribeTokenService.generate(Mockito.anyLong(), Mockito.anyString(), Mockito.anyLong())).thenReturn("tok");
    // Recipient repo stub for seeding logic
    Mockito.when(recipientRepo.countByCampaignIdAndStatus(10L, "PENDING")).thenReturn(0L);
    Mockito.when(recipientRepo.findTop500ByCampaignIdAndStatusOrderByIdAsc(10L, "PENDING")).thenReturn(java.util.List.of());
    // After seeding recipients, service will fetch them again for enqueue
    ArgumentCaptor<CampaignRecipient> recCaptor = ArgumentCaptor.forClass(CampaignRecipient.class);
    Mockito.when(recipientRepo.save(recCaptor.capture())).thenAnswer(inv->inv.getArgument(0));
    ArgumentCaptor<SendingQueueItem> queueCaptor = ArgumentCaptor.forClass(SendingQueueItem.class);
    Mockito.when(queueRepo.save(queueCaptor.capture())).thenAnswer(inv->inv.getArgument(0));

    service.sendNow(1L, 10L);

    // Verify rendered body used (from rendering service)
    boolean anyBodyMatches = queueCaptor.getAllValues().stream().anyMatch(it -> "<p>Hi Ada</p><a href='u'>Unsubscribe</a>".equals(it.getBody()));
    assertThat(anyBodyMatches).isTrue();
    Mockito.verify(renderingService, Mockito.atLeastOnce()).renderDetailed(Mockito.eq(template), Mockito.anyMap());
    }
}
