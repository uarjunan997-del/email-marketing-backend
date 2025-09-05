package com.emailMarketing.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.emailMarketing.analytics.repo.*;
import com.emailMarketing.campaign.*;
import java.time.*;
import java.util.*;

@Service
public class AttributionService {
    private final EcommerceOrderRepository orderRepo; private final CampaignOrderAttributionRepository attrRepo; private final CampaignRepository campaignRepo; private final CampaignRecipientRepository recipientRepo; private final CampaignVariantStatsRepository variantStatsRepo; private final IndustryBenchmarkRepository benchmarkRepo; private final EmailCostOverrideRepository costOverrideRepo; private final CampaignRoiRepository roiRepository; private final CampaignABTestRepository abRepo;
    public AttributionService(EcommerceOrderRepository orderRepo, CampaignOrderAttributionRepository attrRepo, CampaignRepository campaignRepo, CampaignRecipientRepository recipientRepo, CampaignVariantStatsRepository variantStatsRepo, IndustryBenchmarkRepository benchmarkRepo, EmailCostOverrideRepository costOverrideRepo, CampaignRoiRepository roiRepository, CampaignABTestRepository abRepo){
        this.orderRepo=orderRepo; this.attrRepo=attrRepo; this.campaignRepo=campaignRepo; this.recipientRepo=recipientRepo; this.variantStatsRepo=variantStatsRepo; this.benchmarkRepo=benchmarkRepo; this.costOverrideRepo=costOverrideRepo; this.roiRepository=roiRepository; this.abRepo=abRepo; }

    public record OrderIngestResult(Long orderId, boolean created, int attributedCampaigns){}

    @Transactional
    public OrderIngestResult ingestOrder(Long userId, String externalId, String email, String currency, double totalAmount, java.util.Map<String,Object> raw){
        EcommerceOrder existing = orderRepo.findByUserIdAndExternalOrderId(userId, externalId);
        boolean created=false;
        if(existing==null){
            EcommerceOrder o = new EcommerceOrder();
            o.setUserId(userId); o.setExternalOrderId(externalId); o.setCustomerEmail(email); o.setCurrency(currency); o.setTotalAmount(totalAmount); o.setCreatedAt(LocalDateTime.now());
            if(raw!=null){
                o.setJsonPayload(raw.toString());
            }
            existing = orderRepo.save(o); created=true;
        }
        int attributed = attributeOrderToRecentCampaigns(existing);
        return new OrderIngestResult(existing.getId(), created, attributed);
    }

    private int attributeOrderToRecentCampaigns(EcommerceOrder order){
        if(order.getCustomerEmail()==null) return 0;
        LocalDateTime windowStart = order.getCreatedAt().minusDays(30);
        // Find last click or open by scanning recipients table for simplicity (should use events table ideally)
    List<CampaignRecipient> opened = recipientRepo.findByEmailAndFirstOpenAtAfter(order.getCustomerEmail(), windowStart);
    List<CampaignRecipient> clicked = recipientRepo.findByEmailAndFirstClickAtAfter(order.getCustomerEmail(), windowStart);
    List<CampaignRecipient> recents = new ArrayList<>();
    if(opened!=null) recents.addAll(opened);
    if(clicked!=null) recents.addAll(clicked);
    if(recents.isEmpty()) return 0;
        // Pick most recent by engagement timestamp (click preferred over open)
        recents.sort((a,b)->{
            LocalDateTime atA = a.getFirstClickAt()!=null? a.getFirstClickAt(): a.getFirstOpenAt();
            LocalDateTime atB = b.getFirstClickAt()!=null? b.getFirstClickAt(): b.getFirstOpenAt();
            if(atA==null && atB==null) return 0; if(atA==null) return 1; if(atB==null) return -1; return atB.compareTo(atA);
        });
        CampaignRecipient top = recents.get(0);
        double amount = order.getTotalAmount();
        CampaignOrderAttribution attr = new CampaignOrderAttribution();
        attr.setUserId(order.getUserId()); attr.setCampaignId(top.getCampaignId()); attr.setOrderId(order.getId()); attr.setAttributionType(top.getFirstClickAt()!=null?"LAST_CLICK":"LAST_OPEN"); attr.setWeight(1.0); attr.setAttributedAmount(amount); attr.setCreatedAt(LocalDateTime.now());
        attrRepo.save(attr);
        // Update per-campaign ROI aggregate
        updateCampaignRoi(top.getCampaignId());
        // Update variant stats if variant code stored
        if(top.getVariantCode()!=null){ updateVariantStats(top.getCampaignId(), top.getVariantCode()); }
        return 1;
    }

    @Transactional
    public void updateVariantStats(Long campaignId, String variantCode){
        CampaignVariantStats stats = variantStatsRepo.findByCampaignIdAndVariantCode(campaignId, variantCode);
        if(stats==null){ stats = new CampaignVariantStats(); stats.setCampaignId(campaignId); stats.setVariantCode(variantCode); }
        // Basic counts from recipient table
        int sent = recipientRepo.countByCampaignIdAndVariantCode(campaignId, variantCode);
        int opens = recipientRepo.countByCampaignIdAndVariantCodeAndFirstOpenAtIsNotNull(campaignId, variantCode);
        int clicks = recipientRepo.countByCampaignIdAndVariantCodeAndFirstClickAtIsNotNull(campaignId, variantCode);
        stats.setSentCount(sent); stats.setOpenCount(opens); stats.setClickCount(clicks);
        // Revenue from attributions
        double revenue = attrRepo.findByCampaignId(campaignId).stream().filter(a->variantCode.equals(resolveVariantForOrder(a.getOrderId(), campaignId))).mapToDouble(a->a.getAttributedAmount()==null?0:a.getAttributedAmount()).sum();
        stats.setRevenueAmount(revenue);
        stats.setLastCalculatedAt(LocalDateTime.now());
        variantStatsRepo.save(stats);
    }

    private String resolveVariantForOrder(Long orderId, Long campaignId){
        // Simplified: look up recipient with that campaign + email associated with order (requires join not present here).
        return null; // placeholder until enriched events link created.
    }

    @Transactional
    public void updateCampaignRoi(Long campaignId){
        List<CampaignOrderAttribution> atts = attrRepo.findByCampaignId(campaignId);
        double revenue = atts.stream().mapToDouble(a->a.getAttributedAmount()==null?0:a.getAttributedAmount()).sum();
        int orders = (int)atts.stream().map(a->a.getOrderId()).distinct().count();
        CampaignRoi roi = roiRepository.findByCampaignId(campaignId);
        if(roi==null){ roi = new CampaignRoi(); roi.setCampaignId(campaignId); }
        roi.setRevenueAmount(revenue); roi.setAttributedOrders(orders); roi.setLastCalculatedAt(LocalDateTime.now());
        roiRepository.save(roi);
    }

    public record BenchmarkComparison(double userOpenRate, double benchOpenRate, double deltaOpen, double userClickRate, double benchClickRate, double deltaClick){}
    public BenchmarkComparison compareToBenchmark(String vertical, String listTier, double userOpenRate, double userClickRate){
        var bench = benchmarkRepo.findByVerticalAndListTier(vertical, listTier);
        if(bench==null) return new BenchmarkComparison(userOpenRate,0,0,userClickRate,0,0);
        return new BenchmarkComparison(userOpenRate, bench.getAvgOpenRate(), userOpenRate - bench.getAvgOpenRate(), userClickRate, bench.getAvgClickRate(), userClickRate - bench.getAvgClickRate());
    }
}
