package com.emailMarketing.benchmark;

import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import com.emailMarketing.benchmark.repo.*;
import com.emailMarketing.campaign.CampaignRepository;
import java.time.*;
import java.util.*;

@Service
public class BenchmarkService {
    private final BenchmarkMetricRepository metricRepo; private final UserIndustryClassificationRepository classificationRepo; private final BenchmarkComparisonRepository comparisonRepo; private final CampaignRepository campaignRepo; private final com.emailMarketing.attribution.repo.RevenueAttributionRepository revenueRepo; private final com.emailMarketing.analytics.EmailEventRepository eventRepo;
    public BenchmarkService(BenchmarkMetricRepository metricRepo, UserIndustryClassificationRepository classificationRepo, BenchmarkComparisonRepository comparisonRepo, CampaignRepository campaignRepo, com.emailMarketing.attribution.repo.RevenueAttributionRepository revenueRepo, com.emailMarketing.analytics.EmailEventRepository eventRepo){ this.metricRepo=metricRepo; this.classificationRepo=classificationRepo; this.comparisonRepo=comparisonRepo; this.campaignRepo=campaignRepo; this.revenueRepo=revenueRepo; this.eventRepo=eventRepo; }

    // Expanded comparison result including multiple engagement & revenue metrics for dashboard visualization
    public record ComparisonResult(
        String industry,
        String listTier,
        String region,
        double openRateUser,
        double openRateMedian,
        double openRatePercentile,
        double clickRateUser,
        double clickRateMedian,
        double clickRatePercentile,
        double conversionRateUser,
        double conversionRateMedian,
        double conversionRatePercentile,
        double revenuePerEmailUser,
        double revenuePerEmailMedian,
        double revenuePerEmailPercentile,
        double bounceRateUser,
        double bounceRateMedian,
        double bounceRatePercentile,
        String performanceScore,
        List<String> recommendations,
        Map<String,Object> chart,
        Map<String,Object> composite
    ){ }

    public List<String> industries(){
        return metricRepo.findAll().stream().map(BenchmarkMetric::getIndustry).distinct().sorted().toList();
    }

    public String deriveListTier(int contacts){
        if(contacts < 1000) return "TIER1"; if(contacts < 10000) return "TIER2"; if(contacts < 100000) return "TIER3"; return "TIER4";
    }

    @Transactional
    @Cacheable(value="benchmark_compare", key="#userId + ':' + #industry + ':' + #listTier + ':' + #region")
    public ComparisonResult compare(Long userId, String industry, String listTier, String region){
        BenchmarkMetric metric = metricRepo.findByIndustryAndListTierAndRegion(industry, listTier, region);
        if(metric==null) throw new IllegalArgumentException("Benchmark not found");
        // Aggregate user performance across campaigns (basic rollup)
        var campaigns = campaignRepo.findByUserId(userId);
        int sentSum=0, openSum=0, clickSum=0; // bounce not tracked on campaign directly yet
        for(var c: campaigns){ sentSum += c.getSentCount(); openSum += c.getOpenCount(); clickSum += c.getClickCount(); }
        double userOpenRate = sentSum==0?0:(double)openSum/sentSum;
        double userClickRate = sentSum==0?0:(double)clickSum/sentSum;
        // Placeholder: conversion rate and revenue/email would leverage attribution + order ingestion (future integration)
        // Derive conversions as distinct orders (multi-touch aggregated) for a default model (e.g., LAST_CLICK if exists; fallback any)
        String defaultModel = "LAST_CLICK"; // could be configurable
        List<Long> orderIds = revenueRepo.distinctOrderIdsForUserAndModel(userId, defaultModel);
        if(orderIds.isEmpty()){
            // fallback to any model
            orderIds = revenueRepo.findByUserId(userId).stream().map(r->r.getOrderId()).distinct().limit(10000).toList();
        }
        int conversions = orderIds.size();
        double userConversionRate = sentSum==0?0:(double)conversions/sentSum;
        double totalRevenue = revenueRepo.findByUserIdAndModelCode(userId, defaultModel).stream().mapToDouble(r-> r.getAttributedAmount()==null?0.0:r.getAttributedAmount()).sum();
        if(totalRevenue==0){
            totalRevenue = revenueRepo.findByUserId(userId).stream().mapToDouble(r-> r.getAttributedAmount()==null?0.0:r.getAttributedAmount()).sum();
        }
        double userRevenuePerEmail = sentSum==0?0: totalRevenue / sentSum;
        // Bounce rate via events last 30 days
        var since = LocalDateTime.now().minusDays(30);
        long recentSent = eventRepo.countSentSince(userId, since);
        long recentBounces = eventRepo.countBouncesSince(userId, since);
        double userBounceRate = recentSent==0?0:(double)recentBounces/recentSent;

        // Percentiles
        double openPct = approximatePercentile(userOpenRate, metric.getOpenRateBottomQ(), metric.getOpenRateMedian(), metric.getOpenRateTopQ());
        double clickPct = approximatePercentile(userClickRate, metric.getClickRateBottomQ(), metric.getClickRateMedian(), metric.getClickRateTopQ());
        double convPct = approximatePercentile(userConversionRate, metric.getConversionRateBottomQ(), metric.getConversionRateMedian(), metric.getConversionRateTopQ());
        double revPct = approximatePercentile(userRevenuePerEmail, metric.getRevenuePerEmailBottomQ(), metric.getRevenuePerEmailMedian(), metric.getRevenuePerEmailTopQ());
        double bouncePct = approximatePercentileInverse(userBounceRate, metric.getBounceRateBottomQ(), metric.getBounceRateMedian(), metric.getBounceRateTopQ());

        // Composite weighted score (open 30, click 25, conversion 25, revenue 15, bounce 5 inverted)
        double compositeScore = (openPct*0.30) + (clickPct*0.25) + (convPct*0.25) + (revPct*0.15) + (bouncePct*0.05);
        String performance = compositeScore >= 75 ? "STRONG" : compositeScore >= 50 ? "AVERAGE" : "WEAK";
        List<String> recs = multiMetricRecommendations(userOpenRate, userClickRate, userConversionRate, userRevenuePerEmail, userBounceRate, metric);

        Map<String,Object> chart = Map.of(
            "radar", List.of(
                Map.of("metric","open","user", userOpenRate, "median", metric.getOpenRateMedian(), "topQuartile", metric.getOpenRateTopQ()),
                Map.of("metric","click","user", userClickRate, "median", metric.getClickRateMedian(), "topQuartile", metric.getClickRateTopQ()),
                Map.of("metric","conversion","user", userConversionRate, "median", metric.getConversionRateMedian(), "topQuartile", metric.getConversionRateTopQ()),
                Map.of("metric","revenue_per_email","user", userRevenuePerEmail, "median", metric.getRevenuePerEmailMedian(), "topQuartile", metric.getRevenuePerEmailTopQ()),
                Map.of("metric","bounce","user", userBounceRate, "median", metric.getBounceRateMedian(), "topQuartile", metric.getBounceRateTopQ())
            )
        );
        Map<String,Object> composite = Map.of(
            "score", Math.round(compositeScore*100.0)/100.0,
            "percentiles", Map.of(
                "open", openPct,
                "click", clickPct,
                "conversion", convPct,
                "revenuePerEmail", revPct,
                "bounce", bouncePct
            )
        );

        // Persist comparison snapshot (open rate required fields now plus click, etc.)
        BenchmarkComparison comp = new BenchmarkComparison();
        comp.setUserId(userId); comp.setIndustry(industry); comp.setListTier(listTier); comp.setRegion(region); comp.setComputedAt(LocalDateTime.now());
        comp.setOpenRateUser(userOpenRate); comp.setOpenRateMedian(metric.getOpenRateMedian()); comp.setOpenRatePercentile(openPct);
        comp.setClickRateUser(userClickRate); comp.setClickRateMedian(metric.getClickRateMedian()); comp.setClickRatePercentile(clickPct);
        comp.setConversionRateUser(userConversionRate); comp.setConversionRateMedian(metric.getConversionRateMedian()); comp.setConversionRatePercentile(convPct);
        comp.setRevenuePerEmailUser(userRevenuePerEmail); comp.setRevenuePerEmailMedian(metric.getRevenuePerEmailMedian()); comp.setRevenuePerEmailPercentile(revPct);
        comp.setBounceRateUser(userBounceRate); comp.setBounceRateMedian(metric.getBounceRateMedian()); comp.setBounceRatePercentile(bouncePct);
        comp.setPerformanceScore(performance); comp.setRecommendations(String.join("\n", recs));
        comparisonRepo.save(comp);

        return new ComparisonResult(
            industry,listTier,region,
            userOpenRate, metric.getOpenRateMedian(), openPct,
            userClickRate, metric.getClickRateMedian(), clickPct,
            userConversionRate, metric.getConversionRateMedian(), convPct,
            userRevenuePerEmail, metric.getRevenuePerEmailMedian(), revPct,
            userBounceRate, metric.getBounceRateMedian(), bouncePct,
            performance, recs, chart, composite
        );
    }

    private double approximatePercentile(double value, Double bottom, Double median, Double top){
        if(bottom==null||median==null||top==null) return 0;
        if(value<=bottom) return 10;
        if(value>=top) return 95;
        if(value<=median){ return 10 + (value-bottom)/(median-bottom)*40; }
        return 50 + (value-median)/(top-median)*45;
    }

    // For inverse metrics like bounce (lower is better) we invert the scale
    private double approximatePercentileInverse(double value, Double bottom, Double median, Double top){
        if(bottom==null||median==null||top==null) return 0; // bottom=best, top=worst for bounce
        if(value<=bottom) return 95; // best
        if(value>=top) return 10; // worst
        if(value<=median){ // between bottom and median degrade from 95 downward
            return 95 - (value-bottom)/(median-bottom)*45; // up to -45 -> 50
        }
        return 50 - (value-median)/(top-median)*40; // to 10
    }

    private List<String> recommendations(double userOpen, BenchmarkMetric m){
        List<String> r = new ArrayList<>();
        if(userOpen < m.getOpenRateMedian()*0.9) r.add("Improve subject line relevance & preheader testing to reach median.");
        if(userOpen < m.getOpenRateTopQ()*0.8) r.add("Introduce send-time optimization based on engagement heatmap.");
        if(userOpen < m.getOpenRateTopQ()*0.9) r.add("Refine segmentation (recent actives vs dormant) to lift open rate.");
        if(r.isEmpty()) r.add("Maintain experimentation cadence to defend top quartile position.");
        return r;
    }

    private List<String> multiMetricRecommendations(double open, double click, double conv, double rev, double bounce, BenchmarkMetric m){
        List<String> out = new ArrayList<>();
        if(open < safe(m.getOpenRateMedian())*0.9) out.add("Focus on subject line A/B tests; target +10% open uplift.");
        if(click < safe(m.getClickRateMedian())*0.9) out.add("Improve CTA clarity and above-the-fold placement.");
        if(conv < safe(m.getConversionRateMedian())*0.9) out.add("Audit post-click landing pages for friction (speed, form length).");
        if(rev < safe(m.getRevenuePerEmailMedian())*0.85) out.add("Introduce personalized product blocks or dynamic bundles.");
        if(bounce > safe(m.getBounceRateMedian())*1.1) out.add("Run list hygiene & remove dormant hard bounces.");
        if(out.isEmpty()) out.add("Performance strong across core metrics; shift focus to incremental testing & lifecycle automation.");
        return out;
    }

    private double safe(Double v){ return v==null?0.0:v; }

    @Transactional
    public UserIndustryClassification classify(Long userId, String declaredIndustry, int contacts){
        // Simple heuristic: trust declared industry; fallback based on campaign subjects (future NLP)
        UserIndustryClassification c = new UserIndustryClassification();
        c.setUserId(userId); c.setIndustry(declaredIndustry==null?"ECOMMERCE":declaredIndustry.toUpperCase()); c.setConfidence(0.85); c.setMethod("DECLARED_V1"); c.setCreatedAt(LocalDateTime.now());
        return classificationRepo.save(c);
    }

    public List<BenchmarkComparison> recentComparisons(Long userId){
        return comparisonRepo.findTop30ByUserIdOrderByComputedAtDesc(userId);
    }
}
