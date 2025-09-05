package com.emailMarketing.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {
    List<CampaignRecipient> findTop500ByCampaignIdAndStatusOrderByIdAsc(Long campaignId, String status);
    long countByCampaignIdAndStatus(Long campaignId, String status);
    CampaignRecipient findFirstByCampaignIdAndEmail(Long campaignId, String email);
    java.util.List<CampaignRecipient> findByCampaignIdAndStatusIn(Long campaignId, java.util.Collection<String> statuses);
    // Attribution helpers (may require added columns or indexes in future migration)
    java.util.List<CampaignRecipient> findByEmailAndFirstOpenAtAfter(String email, java.time.LocalDateTime after);
    java.util.List<CampaignRecipient> findByEmailAndFirstClickAtAfter(String email, java.time.LocalDateTime after);
    // Variant stats
    int countByCampaignIdAndVariantCode(Long campaignId, String variantCode);
    int countByCampaignIdAndVariantCodeAndFirstOpenAtIsNotNull(Long campaignId, String variantCode);
    int countByCampaignIdAndVariantCodeAndFirstClickAtIsNotNull(Long campaignId, String variantCode);
}
