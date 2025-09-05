package com.emailMarketing.roi;

import com.emailMarketing.roi.repo.CampaignCostRepository; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.jdbc.core.JdbcTemplate; import java.time.*; import java.util.*;

@Service
public class CostAggregationService {
    private final JdbcTemplate jdbc; private final com.emailMarketing.roi.repo.CostAllocationRepository allocationRepo; private final com.emailMarketing.roi.repo.CostAllocationLinkRepository linkRepo;
    public CostAggregationService(CampaignCostRepository c, JdbcTemplate jdbc, com.emailMarketing.roi.repo.CostAllocationRepository allocationRepo, com.emailMarketing.roi.repo.CostAllocationLinkRepository linkRepo){ this.jdbc=jdbc; this.allocationRepo=allocationRepo; this.linkRepo=linkRepo; }

    @Transactional
    public Map<String,Object> aggregateDay(LocalDate day){
        var costs = jdbc.query("SELECT ORG_ID, SUM(AMOUNT) AMT, MIN(CURRENCY) CURR FROM CAMPAIGN_COSTS WHERE COST_DATE=? GROUP BY ORG_ID",
            ps->{ ps.setObject(1, java.sql.Date.valueOf(day)); }, rs->{
                List<Map<String,Object>> list = new ArrayList<>();
                while(rs.next()) list.add(Map.of("orgId", rs.getLong("ORG_ID"), "amount", rs.getDouble("AMT"), "currency", rs.getString("CURR"))); return list; });
        Map<Long,Double> orgTotals = new HashMap<>(); Map<Long,String> orgCurrency = new HashMap<>();
        if(costs!=null){ for(var row: costs){ Long orgId=(Long)row.get("orgId"); if(orgId==null) continue; double amt = ((Number)row.get("amount")).doubleValue(); orgTotals.merge(orgId, amt, Double::sum); String curr=(String)row.get("currency"); if(curr!=null) orgCurrency.putIfAbsent(orgId,curr); }}

        // Allocation sharing: fetch allocations overlapping the day and distribute
        var allocations = allocationRepo.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(day, day);
        for(var alloc: allocations){
            long span = java.time.temporal.ChronoUnit.DAYS.between(alloc.getPeriodStart(), alloc.getPeriodEnd())+1; if(span<=0) span=1;
            double dailyPortion = (alloc.getTotalCost()==null?0: alloc.getTotalCost()) / span;
            var links = linkRepo.findByIdAllocationId(alloc.getId());
            double weightSum = links.stream().mapToDouble(l-> l.getWeightValue()==null?0: l.getWeightValue()).sum(); if(weightSum<=0) weightSum=1;
            for(var link: links){
                double weight = (link.getWeightValue()==null?0: link.getWeightValue())/weightSum; double add = dailyPortion * weight;
                Long orgId = alloc.getOrgId(); if(orgId==null) continue; orgTotals.merge(orgId, add, Double::sum); if(alloc.getCurrency()!=null) orgCurrency.putIfAbsent(orgId, alloc.getCurrency());
            }
        }

        int inserted=0; for(var e: orgTotals.entrySet()){
            Long orgId = e.getKey(); double amt = e.getValue(); String curr = orgCurrency.getOrDefault(orgId, "USD");
            jdbc.update("DELETE FROM COST_DAILY_AGG WHERE ORG_ID=? AND COST_DATE=?", orgId, java.sql.Date.valueOf(day));
            jdbc.update("INSERT INTO COST_DAILY_AGG(ORG_ID,COST_DATE,TOTAL_COST,BASE_CURRENCY) VALUES(?,?,?,?)", orgId, java.sql.Date.valueOf(day), amt, curr);
            inserted++;
        }
        return Map.of("status","OK","day", day, "inserted", inserted, "orgCount", orgTotals.size(), "allocations", allocations.size());
    }

    @Transactional
    public Map<String,Object> backfill(LocalDate start, LocalDate end){
        if(start==null|| end==null) return Map.of("status","ERROR","message","null range");
        if(end.isBefore(start)) return Map.of("status","ERROR","message","end before start");
        LocalDate iter=start; int days=0; int totalRows=0; while(!iter.isAfter(end)){ var res = aggregateDay(iter); totalRows += ((Number)res.getOrDefault("inserted",0)).intValue(); days++; iter=iter.plusDays(1);} return Map.of("status","OK","days", days, "rows", totalRows);
    }
}
