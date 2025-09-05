package com.emailMarketing.roi;

import com.emailMarketing.roi.repo.*; import com.emailMarketing.cache.CacheInvalidationPublisher;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*; import java.util.*; import java.util.stream.*;

@Service
public class RoiService {
    private final CampaignCostRepository costRepo; private final ExchangeRateRepository rateRepo; private final CostCategoryRepository catRepo; private final RoiGoalRepository goalRepo; private final CustomerConversionRepository convRepo; private final RoiBenchmarkRepository benchRepo; private final RoiAnalysisRepository analysisRepo; private final JdbcTemplate jdbc; private final CacheInvalidationPublisher cachePublisher;
    private final String baseCurrency;
    public RoiService(CampaignCostRepository c, ExchangeRateRepository r, RoiAnalysisRepository rr, CostCategoryRepository catRepo, RoiGoalRepository goalRepo, CustomerConversionRepository convRepo, RoiBenchmarkRepository benchRepo, RoiAnalysisRepository analysisRepo, JdbcTemplate jdbc, CacheInvalidationPublisher cachePublisher,
                      @Value("${finance.base-currency:USD}") String baseCurrency){ this.costRepo=c; this.rateRepo=r; this.catRepo=catRepo; this.goalRepo=goalRepo; this.convRepo=convRepo; this.benchRepo=benchRepo; this.analysisRepo=analysisRepo; this.jdbc=jdbc; this.cachePublisher=cachePublisher; this.baseCurrency=baseCurrency; }

    public record AddCostRequest(@jakarta.validation.constraints.NotBlank String categoryCode,
                                 String subcategory,
                                 @jakarta.validation.constraints.NotNull LocalDate costDate,
                                 @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Double amount,
                                 String currency,
                                 @jakarta.validation.constraints.PositiveOrZero Double quantity,
                                 @jakarta.validation.constraints.PositiveOrZero Double unitCost,
                                 String notes,
                                 Long orgId){}
    public record RoiGoalRequest(Long campaignId, Double targetRoiPct, Double targetMarginPct){}
    public record Recommendation(String type, String message, double potentialImpactPct){}

    @Transactional
    public List<CampaignCost> addCampaignCosts(Long campaignId, List<AddCostRequest> costs){
        List<CampaignCost> out = new ArrayList<>();
        for(var req: costs){
            CampaignCost cc = new CampaignCost(); cc.setCampaignId(campaignId); cc.setCategoryCode(req.categoryCode()); cc.setSubcategory(req.subcategory()); cc.setCostDate(req.costDate());
            cc.setAmount(req.amount()); cc.setCurrency(req.currency()==null? baseCurrency: req.currency().toUpperCase());
            cc.setQuantity(req.quantity()); cc.setUnitCost(req.unitCost()); cc.setNotes(req.notes());
            cc.setOrgId(req.orgId());
            out.add(costRepo.save(cc));
        }
        // Emit cache invalidation for each campaign once
        if(!out.isEmpty()){
            cachePublisher.invalidateCampaign(campaignId);
        }
        return out;
    }

    public List<CostCategory> categories(){ return catRepo.findAll(); }

    public Map<String,Object> campaignRoi(Long campaignId, int days){
        LocalDate to = LocalDate.now(); LocalDate from = to.minusDays(days-1);
        List<CampaignCost> costs = costRepo.findByCampaignIdAndCostDateBetween(campaignId, from, to);
        double totalCostBase = costs.stream().mapToDouble(this::toBaseAmount).sum();
        double revenueBase = fetchCampaignRevenueBase(campaignId, from, to);
        double roiPct = totalCostBase==0?0: ((revenueBase - totalCostBase)/ totalCostBase)*100.0;
        double profitMarginPct = revenueBase==0?0: ((revenueBase - totalCostBase)/ revenueBase)*100.0;
        Double paybackDays = calcPaybackDays(costs, revenueBase, from, to);
        Map<String,Double> byCategory = costs.stream().collect(Collectors.groupingBy(CampaignCost::getCategoryCode, LinkedHashMap::new, Collectors.summingDouble(this::toBaseAmount)));
        var goals = goalRepo.findByCampaignId(campaignId).orElse(null);
        List<Recommendation> recs = generateRecommendations(roiPct, profitMarginPct, byCategory, goals);
    Map<String,Object> resp = new LinkedHashMap<>();
    resp.put("status","OK"); resp.put("campaignId", campaignId); resp.put("period", Map.of("from", from, "to", to)); resp.put("baseCurrency", baseCurrency);
    resp.put("totalCostBase", round(totalCostBase)); resp.put("totalRevenueBase", round(revenueBase)); resp.put("roiPct", round(roiPct)); resp.put("profitMarginPct", round(profitMarginPct)); resp.put("paybackDays", paybackDays);
    if(goals!=null) resp.put("goals", Map.of("targetRoiPct", goals.getTargetRoiPct(), "targetMarginPct", goals.getTargetMarginPct()));
    resp.put("costBreakdown", byCategory.entrySet().stream().map(e-> Map.of("category", e.getKey(), "amount", round(e.getValue()))).toList());
    resp.put("recommendations", recs);
    return resp;
    }

    private Double calcPaybackDays(List<CampaignCost> costs, double revenueBase, LocalDate from, LocalDate to){
        if(revenueBase<=0) return null; long totalDays = Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays(); if(totalDays<=0) return null;
        double dailyRev = revenueBase/ totalDays; List<CampaignCost> ordered = new ArrayList<>(costs); ordered.sort(Comparator.comparing(CampaignCost::getCostDate));
        double cumulative=0; LocalDate endExclusive = to.plusDays(1); int day=0; LocalDate iter=from; while(iter.isBefore(endExclusive)){
            LocalDate current = iter;
            double dayCost = 0; for(CampaignCost c: ordered){ if(c.getCostDate().equals(current)) dayCost += toBaseAmount(c); }
            cumulative += (dailyRev - dayCost); if(cumulative>=0) return (double)day+1; day++; iter=iter.plusDays(1); }
        return null; }

    private double fetchCampaignRevenueBase(Long campaignId, LocalDate from, LocalDate to){
        Double val = jdbc.queryForObject("SELECT NVL(SUM(REVENUE),0) FROM CAMPAIGN_METRICS_DAILY WHERE CAMPAIGN_ID=? AND METRIC_DATE BETWEEN ? AND ?",
            Double.class, campaignId, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
        return val==null?0.0:val;
    }

    private double toBaseAmount(CampaignCost c){ if(c.getCurrency().equalsIgnoreCase(baseCurrency)) return nz(c.getAmount()); double rate = lookupRate(c.getCurrency(), c.getCostDate()); return nz(c.getAmount()) * rate; }
    private double lookupRate(String fromCurrency, LocalDate date){ if(fromCurrency.equalsIgnoreCase(baseCurrency)) return 1.0; return rateRepo.findTopByIdBaseCurrencyAndIdQuoteCurrencyAndIdRateDateLessThanEqualOrderByIdRateDateDesc(baseCurrency, fromCurrency, date)
                .map(er-> 1.0 / nz(er.getRate())).orElse(1.0); }

    public Map<String,Object> roiDashboard(int days){
        LocalDate to = LocalDate.now(); LocalDate from = to.minusDays(days-1);
        Double totalRevenueObj = jdbc.queryForObject("SELECT NVL(SUM(REVENUE),0) FROM CAMPAIGN_METRICS_DAILY WHERE METRIC_DATE BETWEEN ? AND ?",
            Double.class, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
        double totalRevenue = totalRevenueObj==null?0.0: totalRevenueObj;
        Double totalCostObj = jdbc.queryForObject("SELECT NVL(SUM(TOTAL_COST),0) FROM COST_DAILY_AGG WHERE COST_DATE BETWEEN ? AND ?",
            Double.class, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
        double totalCost = totalCostObj==null?0.0: totalCostObj;
        double roiPct = totalCost==0?0: ((totalRevenue - totalCost)/ totalCost)*100.0;
        return Map.of("status","OK","days", days, "aggregateRoiPct", round(roiPct), "totalCostBase", round(totalCost), "totalRevenueBase", round(totalRevenue));
    }

    public Map<String,Object> roiTrends(int days){
        LocalDate to = LocalDate.now(); LocalDate from = to.minusDays(days-1);
        List<Map<String,Object>> series = new ArrayList<>();
        LocalDate iter=from; while(!iter.isAfter(to)){
            LocalDate current = iter;
            Double revObj = jdbc.queryForObject("SELECT NVL(SUM(REVENUE),0) FROM CAMPAIGN_METRICS_DAILY WHERE METRIC_DATE=?", Double.class, java.sql.Date.valueOf(current));
            Double costObj = jdbc.queryForObject("SELECT NVL(SUM(TOTAL_COST),0) FROM COST_DAILY_AGG WHERE COST_DATE=?", Double.class, java.sql.Date.valueOf(current));
            double rev = revObj==null?0:revObj; double cost = costObj==null?0:costObj; double roi = cost==0?0: ((rev-cost)/cost)*100.0;
            series.add(Map.of("date", current, "roiPct", round(roi), "revenue", round(rev), "cost", round(cost)));
            iter=iter.plusDays(1);
        }
        return Map.of("status","OK","series", series);
    }

    // Channel-level ROI (email vs other channels) placeholder using revenue ratios
    public Map<String,Object> channelRoi(Long orgId, int days){
        LocalDate to = LocalDate.now(); LocalDate from = to.minusDays(days-1);
        double emailRevenue = jdbc.queryForObject("SELECT NVL(SUM(REVENUE),0) FROM CAMPAIGN_METRICS_DAILY WHERE METRIC_DATE BETWEEN ? AND ?", Double.class, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
        double otherRevenue = 0; // integrate when multi-channel data available
        return Map.of("status","OK","emailRevenue", emailRevenue, "otherRevenue", otherRevenue, "emailSharePct", emailRevenue==0?0: round(emailRevenue/(emailRevenue+otherRevenue)*100.0));
    }

    // Simple LTV attribution (sum of attributed revenue / customers) placeholder until customer tables integrated
    public Map<String,Object> ltvAttribution(Long campaignId, int days){
        LocalDate to = LocalDate.now(); LocalDate from = to.minusDays(days-1);
        var conv = convRepo.findByCampaignIdAndConversionDateBetween(campaignId, from, to);
        double rev = conv.stream().mapToDouble(c-> nz(c.getRevenueAmount())).sum();
        double lifetime = conv.stream().mapToDouble(c-> nz(c.getLifetimeValue())).sum();
        long uniqueCustomers = conv.stream().map(CustomerConversion::getCustomerId).distinct().count();
        double avgLtv = uniqueCustomers==0?0: lifetime/uniqueCustomers;
        return Map.of("status","OK","campaignId", campaignId, "attributedRevenue", round(rev), "uniqueCustomers", uniqueCustomers, "totalLifetimeValue", round(lifetime), "avgCustomerLTV", round(avgLtv));
    }

    // Allocation apportionment logic example (weights normalized)
    public Map<String,Object> apportionAllocation(double totalCost, Map<Long,Double> campaignWeights){
        double sum = campaignWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map<String,Object>> splits = new ArrayList<>();
        for(var e: campaignWeights.entrySet()){
            double pct = sum==0?0: e.getValue()/sum; splits.add(Map.of("campaignId", e.getKey(), "allocatedCost", round(totalCost*pct))); }
        return Map.of("status","OK","totalCost", round(totalCost), "splits", splits);
    }

    // Forecasting stub (could use moving average on revenue series)
    public Map<String,Object> forecastOrgRoi(Long orgId, int days, int horizon){
        if(horizon>60) horizon=60; if(days<30) days=30;
        LocalDate to = LocalDate.now(); LocalDate from = to.minusDays(days-1);
        List<Double> roiSeries = new ArrayList<>();
        LocalDate iter = from; while(!iter.isAfter(to)){
            Double revObj = jdbc.queryForObject("SELECT NVL(SUM(REVENUE),0) FROM CAMPAIGN_METRICS_DAILY WHERE METRIC_DATE=?", Double.class, java.sql.Date.valueOf(iter));
            Double costObj = jdbc.queryForObject("SELECT NVL(SUM(TOTAL_COST),0) FROM COST_DAILY_AGG WHERE COST_DATE=?", Double.class, java.sql.Date.valueOf(iter));
            double rev = revObj==null?0:revObj; double cost = costObj==null?0:costObj; double roi = cost==0?0: ((rev-cost)/cost)*100.0; roiSeries.add(roi); iter=iter.plusDays(1);
        }
        if(roiSeries.size()<14) return Map.of("status","INSUFFICIENT_DATA");
        double[] data = roiSeries.stream().mapToDouble(d->d).toArray();
        // Moving average baseline
        int maWindow = Math.min(7, data.length);
        double lastMa = 0; for(int i=Math.max(0,data.length-maWindow); i<data.length; i++){ lastMa += data[i]; } lastMa/=maWindow;
        // Simple Holt-Winters on ROI (season=7)
        HoltWintersAdd hw = new HoltWintersAdd(0.3,0.05,0.2,7); for(double v: data) hw.update(v);
        List<Map<String,Object>> points = new ArrayList<>();
        for(int i=1;i<=horizon;i++){ double f = hw.forecast(i); double combo = (f + lastMa)/2.0; points.add(Map.of("dayOffset", i, "roiForecastPct", round(combo))); }
        return Map.of("status","OK","horizon", horizon, "points", points);
    }

    @Transactional public Map<String,Object> setRoiGoals(List<RoiGoalRequest> reqs){
        int updated=0; for(var r: reqs){ var goal = goalRepo.findByCampaignId(r.campaignId()).orElseGet(()->{ var g=new RoiGoal(); g.setCampaignId(r.campaignId()); return g; });
            goal.setTargetRoiPct(r.targetRoiPct()); goal.setTargetMarginPct(r.targetMarginPct()); goal.setUpdatedAt(Instant.now()); goalRepo.save(goal); updated++; }
        if(updated>0){
            // Invalidate benchmarks & each campaign goal updated (simplified: no campaignId list here)
            cachePublisher.invalidateBenchmarks("GENERIC");
        }
        return Map.of("status","OK","updated", updated);
    }

    private List<Recommendation> generateRecommendations(double roiPct, double marginPct, Map<String,Double> breakdown, RoiGoal goals){
        List<Recommendation> list = new ArrayList<>();
        double target = goals==null? 0: nz(goals.getTargetRoiPct());
        if(goals!=null && roiPct < target){ list.add(new Recommendation("IMPROVE_ROI","ROI below target; optimize high-cost categories", target - roiPct)); }
        breakdown.entrySet().stream().sorted((a,b)-> Double.compare(b.getValue(), a.getValue())).limit(2).forEach(e-> list.add(new Recommendation("COST_FOCUS","High cost category: "+ e.getKey(), e.getValue())));
        if(marginPct<20) list.add(new Recommendation("MARGIN","Low profit margin; evaluate pricing or reduce variable costs", 20 - marginPct));
    // Industry benchmark comparison
    var generic = benchRepo.findByIdIndustryCode("GENERIC");
    generic.stream().filter(b-> "ROI_PCT".equalsIgnoreCase(b.getId().getMetric())).findFirst().ifPresent(b->{ double bench=b.getValue(); if(roiPct < bench){ list.add(new Recommendation("BENCHMARK_GAP","ROI below generic benchmark by "+ round(bench - roiPct) + " pts", bench - roiPct)); }});
        return list;
    }

    private double round(double v){ return Math.round(v*10000.0)/10000.0; }
    private double nz(Double v){ return v==null?0:v; }
    public Map<String,Object> benchmarks(String industry){
        var list = benchRepo.findByIdIndustryCode(industry==null?"GENERIC": industry);
        return Map.of("status","OK","industry", industry==null?"GENERIC":industry, "metrics", list.stream().map(b-> Map.of("metric", b.getId().getMetric(), "value", b.getValue())).toList());
    }

    // Persist snapshot to ROI_ANALYSIS
    public Map<String,Object> snapshotCampaignRoi(Long campaignId){
        var roi = campaignRoi(campaignId,30); if(!"OK".equals(roi.get("status"))) return roi;
        RoiAnalysis analysis = new RoiAnalysis(); analysis.setCampaignId(campaignId); analysis.setAnalysisDate(LocalDate.now());
        analysis.setBaseCurrency((String)roi.get("baseCurrency"));
        analysis.setTotalCostBase(((Number)roi.get("totalCostBase")).doubleValue());
        analysis.setTotalRevenueBase(((Number)roi.get("totalRevenueBase")).doubleValue());
        analysis.setRoiPct(((Number)roi.get("roiPct")).doubleValue());
        Object pm = roi.get("profitMarginPct"); if(pm instanceof Number n) analysis.setProfitMarginPct(n.doubleValue());
        analysis.setGeneratedAt(java.time.Instant.now());
        analysisRepo.save(analysis);
        return Map.of("status","OK","saved", true);
    }
}

    // Lightweight Holt-Winters additive for ROI forecasting
