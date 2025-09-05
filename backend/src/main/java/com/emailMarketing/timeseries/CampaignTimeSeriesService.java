package com.emailMarketing.timeseries;

import com.emailMarketing.timeseries.repo.CampaignMetricsTimeseriesRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*; import java.util.*; import java.util.stream.*;

@Service
public class CampaignTimeSeriesService {
    private final CampaignMetricsTimeseriesRepository repo; private final JdbcTemplate jdbc;
    private final double alertOpenRateMin; private final double alertOpenRateDropPct; private final double alertClickRateDropPct; private final double alertConvSpikePct;
    public CampaignTimeSeriesService(CampaignMetricsTimeseriesRepository r, JdbcTemplate jdbc,
        @Value("${alerts.thresholds.open-rate-min:0.15}") double orm,
        @Value("${alerts.thresholds.open-rate-drop-pct:20}") double orDrop,
        @Value("${alerts.thresholds.click-rate-drop-pct:25}") double clickDrop,
        @Value("${alerts.thresholds.conversion-spike-pct:50}") double convSpike){
        this.repo=r; this.jdbc=jdbc; this.alertOpenRateMin=orm; this.alertOpenRateDropPct=orDrop; this.alertClickRateDropPct=clickDrop; this.alertConvSpikePct=convSpike; }

    public record CampaignPoint(LocalDate date, long sent,long delivered,long opened,long clicked,long converted,double revenue){}

    @Cacheable(value="ts_campaign", key="#campaignId + ':' + #days")
    public List<CampaignPoint> campaignSeries(Long campaignId, int days){
        LocalDate to = LocalDate.now(); LocalDate from = to.minusDays(days-1);
        var rows = repo.findByIdCampaignIdAndIdMetricDateBetweenOrderByIdMetricDateAsc(campaignId, from, to);
        return rows.stream().collect(Collectors.groupingBy(r->r.getId().getMetricDate(), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().map(e-> aggregateDay(e.getKey(), e.getValue())).collect(Collectors.toList());
    }

    public Map<String,Object> campaignSeriesPaged(Long campaignId, int days, int page, int size){
        var full = campaignSeries(campaignId, days);
        int fromIdx = Math.min(page*size, full.size());
        int toIdx = Math.min(fromIdx + size, full.size());
        return Map.of("page", page, "size", size, "total", full.size(), "data", full.subList(fromIdx, toIdx));
    }

    private CampaignPoint aggregateDay(LocalDate d, List<CampaignMetricsTimeseries> list){
        long sent=0,deliv=0,open=0,click=0,conv=0; double rev=0; for(var r: list){ sent+=nz(r.getSentCount()); deliv+=nz(r.getDeliveredCount()); open+=nz(r.getOpenedCount()); click+=nz(r.getClickedCount()); conv+=nz(r.getConvertedCount()); rev+=nzD(r.getRevenue()); }
        return new CampaignPoint(d, sent,deliv,open,click,conv,rev);
    }

    @Cacheable(value="dashboard_overview", key="#days")
    public Map<String,Object> dashboardOverview(int days){
        Map<String,Object> row = jdbc.query("SELECT /*+ PARALLEL(4) */ NVL(SUM(SENT_COUNT),0) sent, NVL(SUM(OPENED_COUNT),0) opened, NVL(SUM(DELIVERED_COUNT),0) delivered, NVL(SUM(REVENUE),0) revenue FROM CAMPAIGN_METRICS_DAILY WHERE METRIC_DATE >= TRUNC(SYSDATE)-?+1",
            ps->{ ps.setInt(1, days); }, rs->{ if(rs.next()){ return Map.of(
                "sent", rs.getLong("sent"),
                "opened", rs.getLong("opened"),
                "delivered", rs.getLong("delivered"),
                "openRate", rs.getLong("delivered")==0?0: round((double)rs.getLong("opened")/rs.getLong("delivered")),
                "revenue", rs.getDouble("revenue")
            ); } return Map.of("sent",0,"opened",0,"delivered",0,"openRate",0,"revenue",0); });
        return Map.of("status","OK","days",days,"totals", row);
    }

    public Map<String,Object> trendAnalysis(Long campaignId, int days){
        var series = campaignSeries(campaignId, days);
        Map<String,Object> ma = movingAverages(series);
        String direction = trendDirection(series);
        Map<String,Object> forecast = simpleLinearForecast(series, 14);
    Map<String,Object> decomposition = decomposeWeekly(series);
    Map<String,Object> fcStats = forecastStats(series);
    return Map.of("status","OK","campaignId",campaignId,"series", series, "movingAverages", ma, "trendDirection", direction, "forecast", forecast, "forecastStats", fcStats, "decomposition", decomposition);
    }

    public Map<String,Object> comparePeriods(Long campaignId, int curDays){
        var cur = campaignSeries(campaignId, curDays);
        var prev = campaignSeries(campaignId, curDays*2).subList(0, curDays);
        double curOr = rate(sum(cur, c->c.opened), sum(cur, c->c.delivered));
        double prevOr = rate(sum(prev, c->c.opened), sum(prev, c->c.delivered));
        return Map.of("status","OK","campaignId",campaignId, "openRateDeltaPct", delta(curOr, prevOr));
    }

    public Map<String,Object> seasonalPattern(Long campaignId, int weeks){
        var series = campaignSeries(campaignId, weeks*7);
        Map<Integer,List<Double>> map = new HashMap<>();
        for(var p: series){ int dow = p.date().getDayOfWeek().getValue(); map.computeIfAbsent(dow,k->new ArrayList<>()).add(rate(p.opened,p.delivered)); }
        double overall = map.values().stream().flatMap(List::stream).mapToDouble(d->d).average().orElse(0);
        Map<String,Double> factors = new LinkedHashMap<>(); for(int i=1;i<=7;i++){ double avg = map.getOrDefault(i,List.of()).stream().mapToDouble(d->d).average().orElse(0); factors.put(dayName(i), overall==0?1: round(avg/overall)); }
        return Map.of("status","OK","campaignId",campaignId,"baseline", overall, "factors", factors);
    }

    public Map<String,Object> performanceAlerts(Long campaignId, int days, double openRateThreshold){
        var series = campaignSeries(campaignId, days); if(series.isEmpty()) return Map.of("status","NO_DATA");
        double latest = rate(series.get(series.size()-1).opened, series.get(series.size()-1).delivered);
        double latestClickRate = rate(series.get(series.size()-1).clicked, series.get(series.size()-1).delivered);
        double latestConvRate = rate(series.get(series.size()-1).converted, series.get(series.size()-1).delivered);
        // rolling baselines
        double orBaseline = rollingBaseline(series, c-> rate(c.opened,c.delivered), 30);
        double clickBaseline = rollingBaseline(series, c-> rate(c.clicked,c.delivered), 30);
        double convBaseline = rollingBaseline(series, c-> rate(c.converted,c.delivered), 30);
        List<String> alerts = new ArrayList<>();
        double orDropFactor = 1 - (alertOpenRateDropPct/100.0);
        double clickDropFactor = 1 - (alertClickRateDropPct/100.0);
        double convSpikeFactor = 1 + (alertConvSpikePct/100.0);
        if(latest < Math.min(openRateThreshold, alertOpenRateMin)) alerts.add("OPEN_RATE_BELOW_MIN");
        if(orBaseline>0 && latest < orBaseline*orDropFactor) alerts.add("OPEN_RATE_DROP_"+ (int)alertOpenRateDropPct +"_PCT");
        if(clickBaseline>0 && latestClickRate < clickBaseline*clickDropFactor) alerts.add("CLICK_RATE_DROP_"+ (int)alertClickRateDropPct +"_PCT");
        if(convBaseline>0 && latestConvRate > convBaseline*convSpikeFactor) alerts.add("CONVERSION_SPIKE_"+ (int)alertConvSpikePct +"_PCT");
        return Map.of("status","OK","campaignId",campaignId,"latestOpenRate", latest, "latestClickRate", latestClickRate, "latestConversionRate", latestConvRate,
            "baselines", Map.of("openRate", orBaseline, "clickRate", clickBaseline, "conversionRate", convBaseline),
            "alerts", alerts);
    }

    private Map<String,Object> movingAverages(List<CampaignPoint> series){
        return Map.of(
            "ma7", movingAvg(series,7),
            "ma30", movingAvg(series,30),
            "ma90", movingAvg(series,90)
        );
    }
    private double movingAvg(List<CampaignPoint> s,int w){ if(s.isEmpty()) return 0; int start=Math.max(0,s.size()-w); return s.subList(start,s.size()).stream().mapToDouble(p-> rate(p.opened,p.delivered)).average().orElse(0); }
    private String trendDirection(List<CampaignPoint> s){ if(s.size()<5) return "STABLE"; double[] arr = s.stream().mapToDouble(p-> rate(p.opened,p.delivered)).toArray(); double slope = slope(arr); if(slope>0.0005) return "INCREASING"; if(slope<-0.0005) return "DECREASING"; return "STABLE"; }
    private double slope(double[] arr){ int n=arr.length; double sx=0,sy=0,sxy=0,sx2=0; for(int i=0;i<n;i++){ sx+=i; sy+=arr[i]; sxy+=i*arr[i]; sx2+=i*i;} double denom=n*sx2-sx*sx; if(denom==0) return 0; return (n*sxy-sx*sy)/denom; }
    private Map<String,Object> simpleLinearForecast(List<CampaignPoint> s, int horizon){ if(s.size()<5) return Map.of("status","INSUFFICIENT_DATA"); double[] arr = s.stream().mapToDouble(p-> rate(p.opened,p.delivered)).toArray(); double b=slope(arr); double a=arr[arr.length-1]; List<Map<String,Object>> pts=new ArrayList<>(); for(int i=1;i<=horizon;i++){ double v=Math.max(0,Math.min(1,a + b*i)); pts.add(Map.of("dayOffset",i,"openRateForecast", round(v))); } return Map.of("status","OK","horizon",horizon,"points",pts); }
    @Cacheable(value="ts_campaign_anomalies", key="#campaignId + ':' + #days")
    public Map<String,Object> anomalies(Long campaignId, int days){
        var series = campaignSeries(campaignId, days); if(series.size()<14) return Map.of("status","INSUFFICIENT_DATA");
        double[] rates = series.stream().mapToDouble(p-> rate(p.opened,p.delivered)).toArray();
        double median = percentile(rates,50);
        double[] absDev = Arrays.stream(rates).map(v-> Math.abs(v-median)).toArray();
        double mad = percentile(absDev,50); if(mad==0) return Map.of("status","NO_VARIANCE");
        List<Map<String,Object>> list = new ArrayList<>();
        for(int i=0;i<rates.length;i++){
            double robustZ = 0.6745 * (rates[i]-median)/mad; // approximate normal equivalence
            if(Math.abs(robustZ)>=3){
                list.add(Map.of("date", series.get(i).date().toString(), "openRate", round(rates[i]), "robustZ", round(robustZ)));
            }
        }
        return Map.of("status","OK","median", round(median), "mad", round(mad), "anomalies", list);
    }
    private double percentile(double[] arr,int p){ double[] copy=arr.clone(); Arrays.sort(copy); if(copy.length==0) return 0; double rank = (p/100.0)*(copy.length-1); int lo=(int)Math.floor(rank); int hi=(int)Math.ceil(rank); if(lo==hi) return copy[lo]; double w=rank-lo; return copy[lo]*(1-w)+copy[hi]*w; }
    private Map<String,Object> forecastStats(List<CampaignPoint> s){ if(s.size()<10) return Map.of("status","INSUFFICIENT_DATA"); double[] arr = s.stream().mapToDouble(p-> rate(p.opened,p.delivered)).toArray(); double b=slope(arr); double a=arr[0]; int look=Math.min(7, arr.length); double mae=0; for(int i=0;i<look;i++){ double fitted = a + b*i; mae+=Math.abs(arr[i]-fitted);} mae/=look; double conf = Math.max(0,1 - mae); return Map.of("status","OK","mae", round(mae), "confidence", round(conf)); }
    // Weekly seasonal decomposition (very lightweight): rate = trend + seasonal + residual
    private Map<String,Object> decomposeWeekly(List<CampaignPoint> s){
        if(s.size()<21) return Map.of("status","INSUFFICIENT_DATA");
        int season=7; double[] rates = s.stream().mapToDouble(p-> rate(p.opened,p.delivered)).toArray();
        double[] trend = movingAverage(rates, season);
        double[] seasonal = new double[season]; int[] counts = new int[season];
        for(int i=0;i<rates.length;i++){ if(i<trend.length && !Double.isNaN(trend[i])){ int idx = i%season; seasonal[idx]+= (rates[i]-trend[i]); counts[idx]++; } }
        for(int i=0;i<season;i++){ if(counts[i]>0) seasonal[i]/=counts[i]; }
        double meanSeason = Arrays.stream(seasonal).average().orElse(0); for(int i=0;i<season;i++) seasonal[i]-=meanSeason; // center
        List<Map<String,Object>> trendSeries = new ArrayList<>(); List<Map<String,Object>> seasonalSeries=new ArrayList<>();
        for(int i=0;i<rates.length;i++){ if(i<trend.length && !Double.isNaN(trend[i])) trendSeries.add(Map.of("index",i,"value", round(trend[i]))); }
        for(int i=0;i<season;i++){ seasonalSeries.add(Map.of("dow", dayName(((i+1)) ), "factor", round(seasonal[i]))); }
        return Map.of("status","OK","trend", trendSeries, "seasonalFactors", seasonalSeries);
    }
    private double[] movingAverage(double[] arr,int window){ double[] out=new double[arr.length]; Arrays.fill(out, Double.NaN); double sum=0; for(int i=0;i<arr.length;i++){ sum+=arr[i]; if(i>=window) sum-=arr[i-window]; if(i>=window-1) out[i]= sum/window; } return out; }
    private double rollingBaseline(List<CampaignPoint> s, java.util.function.ToDoubleFunction<CampaignPoint> fn,int window){ if(s.isEmpty()) return 0; int start=Math.max(0,s.size()-window); return s.subList(start,s.size()).stream().mapToDouble(fn).average().orElse(0); }
    private long nz(Long v){return v==null?0:v;} private double nzD(Double v){return v==null?0:v;} private double rate(long part,long total){ return total==0?0: round((double)part/total); }
    private double round(double v){ return Math.round(v*10000.0)/10000.0; } private double delta(double cur,double prev){ if(prev==0) return cur==0?0:100; return round(((cur-prev)/Math.abs(prev))*100.0);} private long sum(List<CampaignPoint> list, java.util.function.ToLongFunction<CampaignPoint> fn){ long t=0; for(var p:list) t+=fn.applyAsLong(p); return t; }
    private String dayName(int dow){return switch(dow){case 1->"MON";case 2->"TUE";case 3->"WED";case 4->"THU";case 5->"FRI";case 6->"SAT";default->"SUN";};}
}
