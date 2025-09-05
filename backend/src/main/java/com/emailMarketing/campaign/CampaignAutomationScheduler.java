package com.emailMarketing.campaign;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CampaignAutomationScheduler {
    private final CampaignService campaignService;
    public CampaignAutomationScheduler(CampaignService campaignService){ this.campaignService=campaignService; }

    // Check every minute for due scheduled campaigns
    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void activateDue(){
        campaignService.activateDueScheduledCampaigns();
    }
}
