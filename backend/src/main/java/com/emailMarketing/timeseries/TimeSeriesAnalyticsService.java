package com.emailMarketing.timeseries;

import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import com.emailMarketing.timeseries.repo.*;
import java.time.*;
import java.util.*;

@Service
public class TimeSeriesAnalyticsService {
    private final EmailMetricHourlyRepository hourlyRepo; private final EmailMetricDailyRepository dailyRepo; private final EmailMetricWeeklyRepository weeklyRepo; private final EmailMetricMonthlyRepository monthlyRepo;
    private final EmailMetricDailyForecastRepository forecastRepo;
    private final double alpha; private final double beta; private final double gamma;
    public TimeSeriesAnalyticsService(EmailMetricHourlyRepository h, EmailMetricDailyRepository d, EmailMetricWeeklyRepository w, EmailMetricMonthlyRepository m, EmailMetricDailyForecastRepository f,
        @Value("${forecast.hw.alpha:0.3}") double alpha,
        @Value("${forecast.hw.beta:0.05}") double beta,
        @Value("${forecast.hw.gamma:0.2}") double gamma){ this.hourlyRepo=h; this.dailyRepo=d; this.weeklyRepo=w; this.monthlyRepo=m; this.forecastRepo=f; this.alpha=alpha; this.beta=beta; this.gamma=gamma; }

    public record Point(LocalDateTime bucket, long sent, long open, long click, long bounce, long orders, double revenue){}
    public record TrendResponse(List<Point> series, Map<String,Object> stats, Map<String,Object> comparisons, Map<String,Object> forecast){ }

    @Cacheable(value="ts_daily", key="#userId + ':' + #days")
    public TrendResponse daily(Long userId, int days){
        LocalDateTime to = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime from = to.minusDays(days-1);
        var rows = dailyRepo.findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(userId, from, to);
        List<Point> pts = new ArrayList<>();
        long sentSum=0, openSum=0, clickSum=0, bounceSum=0, orderSum=0; double revSum=0;
        for(var r: rows){
            pts.add(new Point(r.getId().getBucketStart(), nz(r.getSentCount()), nz(r.getOpenCount()), nz(r.getClickCount()), nz(r.getBounceCount()), nz(r.getOrderCount()), nzD(r.getRevenueAmount())));
            sentSum+=nz(r.getSentCount()); openSum+=nz(r.getOpenCount()); clickSum+=nz(r.getClickCount()); bounceSum+=nz(r.getBounceCount()); orderSum+=nz(r.getOrderCount()); revSum+=nzD(r.getRevenueAmount());
        }
        Map<String,Object> stats = Map.of(
            "sent", sentSum,
            "openRate", rate(openSum, sentSum),
            "clickRate", rate(clickSum, sentSum),
            "bounceRate", rate(bounceSum, sentSum),
            "orderRate", rate(orderSum, sentSum),
            "revPerEmail", sentSum==0?0: round(revSum/sentSum)
        );
        Map<String,Object> comparisons = periodComparisons(userId, from.toLocalDate(), to.toLocalDate());
        Map<String,Object> forecast = simpleForecast(pts);
        return new TrendResponse(pts, stats, comparisons, forecast);
    }

    @Cacheable(value="ts_hourly", key="#userId + ':' + #hours")
    public List<Point> hourly(Long userId, int hours){
        LocalDateTime to = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime from = to.minusHours(hours-1);
        var rows = hourlyRepo.findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(userId, from, to);
        List<Point> pts = new ArrayList<>();
        for(var r: rows){ pts.add(new Point(r.getId().getBucketStart(), nz(r.getSentCount()), nz(r.getOpenCount()), nz(r.getClickCount()), nz(r.getBounceCount()), nz(r.getOrderCount()), nzD(r.getRevenueAmount()))); }
        return pts;
    }

    @Cacheable(value="ts_weekly", key="#userId + ':' + #weeks")
    public TrendResponse weekly(Long userId, int weeks){
        LocalDate today = LocalDate.now();
        LocalDate endWeekMon = today.minusDays((today.getDayOfWeek().getValue()+6)%7); // Monday anchor
        LocalDate start = endWeekMon.minusWeeks(weeks-1);
        var rows = weeklyRepo.findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(userId, start.atStartOfDay(), endWeekMon.atStartOfDay());
        List<Point> pts = new ArrayList<>(); long sent=0, open=0, click=0, bounce=0, orders=0; double rev=0;
        for(var r: rows){
            pts.add(new Point(r.getId().getBucketStart(), nz(r.getSentCount()), nz(r.getOpenCount()), nz(r.getClickCount()), nz(r.getBounceCount()), nz(r.getOrderCount()), nzD(r.getRevenueAmount())));
            sent+=nz(r.getSentCount()); open+=nz(r.getOpenCount()); click+=nz(r.getClickCount()); bounce+=nz(r.getBounceCount()); orders+=nz(r.getOrderCount()); rev+=nzD(r.getRevenueAmount());
        }
        Map<String,Object> stats = Map.of("sent", sent, "openRate", rate(open,sent), "clickRate", rate(click,sent), "bounceRate", rate(bounce,sent), "orderRate", rate(orders,sent), "revPerEmail", sent==0?0:round(rev/sent));
        Map<String,Object> seasonality = weekdaySeasonality(userId, 8); // 8 weeks window
        return new TrendResponse(pts, stats, Map.of(), Map.of("seasonality", seasonality));
    }

    @Cacheable(value="ts_monthly", key="#userId + ':' + #months")
    public TrendResponse monthly(Long userId, int months){
        LocalDate firstOfThis = LocalDate.now().withDayOfMonth(1);
        LocalDate start = firstOfThis.minusMonths(months-1);
        var rows = monthlyRepo.findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(userId, start.atStartOfDay(), firstOfThis.atStartOfDay());
        List<Point> pts = new ArrayList<>(); long sent=0, open=0, click=0, bounce=0, orders=0; double rev=0;
        for(var r: rows){ pts.add(new Point(r.getId().getBucketStart(), nz(r.getSentCount()), nz(r.getOpenCount()), nz(r.getClickCount()), nz(r.getBounceCount()), nz(r.getOrderCount()), nzD(r.getRevenueAmount()))); sent+=nz(r.getSentCount()); open+=nz(r.getOpenCount()); click+=nz(r.getClickCount()); bounce+=nz(r.getBounceCount()); orders+=nz(r.getOrderCount()); rev+=nzD(r.getRevenueAmount()); }
        Map<String,Object> stats = Map.of("sent", sent, "openRate", rate(open,sent), "clickRate", rate(click,sent), "bounceRate", rate(bounce,sent), "orderRate", rate(orders,sent), "revPerEmail", sent==0?0:round(rev/sent));
        Map<String,Object> yoy = yearOverYear(userId);
        return new TrendResponse(pts, stats, Map.of("yoy", yoy), Map.of());
    }

    public Map<String,Object> movingAverages(Long userId, int days){
        var trend = daily(userId, days);
        var series = trend.series();
        double ma7 = movingAvg(series,7,p-> rate(p.open,p.sent));
        double ma28 = movingAvg(series,28,p-> rate(p.open,p.sent));
        double ma84 = movingAvg(series,84,p-> rate(p.open,p.sent));
        return Map.of("ma7", ma7, "ma28", ma28, "ma84", ma84);
    }

    public Map<String,Object> weekdaySeasonality(Long userId, int weeks){
        var trend = daily(userId, weeks*7);
        Map<Integer, List<Double>> map = new HashMap<>();
        for(var p: trend.series()){
            int dow = p.bucket.getDayOfWeek().getValue();
            map.computeIfAbsent(dow, k-> new ArrayList<>()).add(rate(p.open,p.sent));
        }
        Map<String,Double> factors = new LinkedHashMap<>();
        double overall = map.values().stream().flatMap(List::stream).mapToDouble(d->d).average().orElse(0);
        for(int i=1;i<=7;i++){
            double avg = map.getOrDefault(i, List.of()).stream().mapToDouble(d->d).average().orElse(0);
            factors.put(dayName(i), overall==0?1: round(avg/overall));
        }
        return Map.of("baseline", overall, "factors", factors);
    }

    @Cacheable(value="ts_forecast", key="#userId + ':' + #horizonDays")
    public Map<String,Object> forecast(Long userId, int horizonDays){
        if(horizonDays>30) horizonDays=30;
        var history = daily(userId, 120).series(); // last 120 days
        if(history.size()<30) return Map.of("status","INSUFFICIENT_DATA");
        double[] or = history.stream().mapToDouble(p-> rate(p.open,p.sent)).toArray();
        int season = 7;
    HoltWintersAdd hw = new HoltWintersAdd(alpha,beta,gamma, season);
        for(double v: or) hw.update(v);
        List<Map<String,Object>> fpts = new ArrayList<>();
        LocalDateTime lastBucket = history.get(history.size()-1).bucket();
        // Fetch existing forecasts to avoid duplicate inserts
        LocalDateTime from = lastBucket.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime to = lastBucket.plusDays(horizonDays).withHour(0).withMinute(0).withSecond(0).withNano(0);
        var existing = forecastRepo.findByIdUserIdAndIdBucketStartBetweenAndIdModelOrderByIdBucketStartAsc(userId, from, to, "HW_ADD");
        Set<LocalDateTime> existingBuckets = new HashSet<>();
        for(var e: existing){ existingBuckets.add(e.getId().getBucketStart()); }
        List<EmailMetricDailyForecast> batch = new ArrayList<>();
        for(int i=1;i<=horizonDays;i++){
            double fv = hw.forecast(i);
            // clamp range 0..1 just in case
            fv = Math.max(0, Math.min(1, fv));
            LocalDateTime bucket = lastBucket.plusDays(i).withHour(0).withMinute(0).withSecond(0).withNano(0);
            double rounded = round(fv);
            fpts.add(Map.of("bucket", bucket.toString(), "openRateForecast", rounded));
            if(!existingBuckets.contains(bucket)){
                var ent = new EmailMetricDailyForecast();
                ent.setId(new EmailMetricDailyForecast.Pk(userId, bucket, "HW_ADD"));
                ent.setOpenRateForecast(rounded);
                batch.add(ent);
            }
        }
        if(!batch.isEmpty()) forecastRepo.saveAll(batch);
        return Map.of("status","OK","model","HW_ADD","horizon", horizonDays, "points", fpts);
    }

    @Cacheable(value="ts_anomalies", key="#userId + ':' + #days")
    public Map<String,Object> anomalies(Long userId, int days){
        var trend = daily(userId, days);
        var series = trend.series(); if(series.size()<14) return Map.of("status","INSUFFICIENT_DATA");
        double[] rates = series.stream().mapToDouble(p-> rate(p.open,p.sent)).toArray();
        double mean = Arrays.stream(rates).average().orElse(0); double std = Math.sqrt(Arrays.stream(rates).map(v->(v-mean)*(v-mean)).sum()/rates.length);
        List<Map<String,Object>> list = new ArrayList<>();
        for(int i=0;i<rates.length;i++){
            double z = std==0?0:(rates[i]-mean)/std;
            if(Math.abs(z)>=2.5){ list.add(Map.of("bucket", series.get(i).bucket().toString(), "openRate", round(rates[i]), "zScore", round(z))); }
        }
        return Map.of("status","OK","anomalies", list, "mean", round(mean), "std", round(std));
    }

    public Map<String,Object> backtest(Long userId, int trainDays, int testDays){
        if(trainDays<30) trainDays=30; if(testDays>30) testDays=30; if(testDays<7) testDays=7;
        var history = daily(userId, trainDays+testDays).series();
        if(history.size()<trainDays+testDays) return Map.of("status","INSUFFICIENT_DATA");
        List<TimeSeriesAnalyticsService.Point> train = history.subList(0, history.size()-testDays);
        List<TimeSeriesAnalyticsService.Point> test = history.subList(history.size()-testDays, history.size());
        double[] trainRates = train.stream().mapToDouble(p-> rate(p.open,p.sent)).toArray();
        int season=7; HoltWintersAdd hw = new HoltWintersAdd(alpha,beta,gamma,season); for(double v: trainRates) hw.update(v);
        double mae=0, mse=0; List<Map<String,Object>> details = new ArrayList<>();
        for(int i=1;i<=testDays;i++){
            double forecast = hw.forecast(i); double actual = rate(test.get(i-1).open, test.get(i-1).sent);
            double err = Math.abs(actual - forecast); mae+=err; mse+= (actual-forecast)*(actual-forecast);
            details.add(Map.of("bucket", test.get(i-1).bucket().toString(), "forecast", round(forecast), "actual", round(actual), "absError", round(err)));
        }
        mae = round(mae/testDays); mse = round(mse/testDays);
        return Map.of("status","OK","model","HW_ADD","trainDays", trainDays, "testDays", testDays, "MAE", mae, "MSE", mse, "points", details);
    }

    public Map<String,Object> yearOverYear(Long userId){
        LocalDate today = LocalDate.now();
        LocalDate startCur = today.minusDays(29);
        LocalDate startPrev = startCur.minusYears(1);
        LocalDate endPrev = today.minusYears(1);
        var cur = daily(userId,30).series();
        var prev = dailyRepo.findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(userId, startPrev.atStartOfDay(), endPrev.atStartOfDay());
        long sentPrev=0, openPrev=0; for(var r: prev){ sentPrev+=nz(r.getSentCount()); openPrev+=nz(r.getOpenCount()); }
        long sentCur=0, openCur=0; for(var p: cur){ sentCur+=p.sent; openCur+=p.open; }
        double orCur = rate(openCur,sentCur); double orPrev = rate(openPrev,sentPrev);
        return Map.of("openRateCur", orCur, "openRatePrev", orPrev, "openRateDeltaPct", delta(orCur, orPrev));
    }


    private Map<String,Object> periodComparisons(Long userId, LocalDate from, LocalDate to){
        // Simple PoP: previous same length window
        long days = Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays();
        LocalDate prevFrom = from.minusDays(days); LocalDate prevTo = to.minusDays(days);
        var cur = dailyRepo.findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(userId, from.atStartOfDay(), to.atStartOfDay());
        var prev = dailyRepo.findByIdUserIdAndIdBucketStartBetweenOrderByIdBucketStartAsc(userId, prevFrom.atStartOfDay(), prevTo.atStartOfDay());
        long sentCur=0, openCur=0, clickCur=0; for(var r: cur){ sentCur+=nz(r.getSentCount()); openCur+=nz(r.getOpenCount()); clickCur+=nz(r.getClickCount()); }
        long sentPrev=0, openPrev=0, clickPrev=0; for(var r: prev){ sentPrev+=nz(r.getSentCount()); openPrev+=nz(r.getOpenCount()); clickPrev+=nz(r.getClickCount()); }
        return Map.of(
            "openRateDelta", delta(rate(openCur,sentCur), rate(openPrev,sentPrev)),
            "clickRateDelta", delta(rate(clickCur,sentCur), rate(clickPrev,sentPrev)),
            "volumeDelta", delta(sentCur, sentPrev)
        );
    }

    private Map<String,Object> simpleForecast(List<Point> pts){
        if(pts.size()<7) return Map.of("status","INSUFFICIENT_DATA");
        // naive seasonal (weekly) average + last trend slope for next 7 days
        int period = 7;
        double[] openRates = pts.stream().mapToDouble(p-> rate(p.open, p.sent)).toArray();
        double slope = trendSlope(openRates);
        List<Map<String,Object>> next = new ArrayList<>();
        for(int i=1;i<=7;i++){
            double base = openRates[Math.max(0, openRates.length - period + (i-1)) % openRates.length];
            double forecast = Math.max(0, Math.min(1, base + slope*i));
            next.add(Map.of("dayOffset", i, "openRateForecast", round(forecast)));
        }
        return Map.of("status","OK","horizon",7,"points", next);
    }

    private double trendSlope(double[] arr){
        int n = arr.length; if(n<2) return 0;
        double sumX=0,sumY=0,sumXY=0,sumX2=0; for(int i=0;i<n;i++){ sumX+=i; sumY+=arr[i]; sumXY+=i*arr[i]; sumX2+=i*i; }
        double denom = n*sumX2 - sumX*sumX; if(denom==0) return 0; return (n*sumXY - sumX*sumY)/denom;
    }

    private long nz(Integer v){ return v==null?0L:v; }
    private double nzD(Double v){ return v==null?0.0:v; }
    private double rate(long part,long total){ return total==0?0: round((double)part/total); }
    private double round(double v){ return Math.round(v*10000.0)/10000.0; }
    private double delta(double cur,double prev){ if(prev==0) return cur==0?0:100; return round(((cur-prev)/Math.abs(prev))*100.0); }

    private double movingAvg(List<Point> pts, int window, java.util.function.ToDoubleFunction<Point> fn){
        if(pts.isEmpty()) return 0; int start=Math.max(0, pts.size()-window); return pts.subList(start, pts.size()).stream().mapToDouble(fn).average().orElse(0); }
    private String dayName(int dow){ return switch(dow){case 1->"MON";case 2->"TUE";case 3->"WED";case 4->"THU";case 5->"FRI";case 6->"SAT";default->"SUN";}; }

    // Simple Holt-Winters additive implementation
    static class HoltWintersAdd {
        private final double alpha, beta, gamma; private final int seasonLength; private double level=0, trend=0; private double[] seasonals; private int count=0;
        HoltWintersAdd(double a,double b,double g,int s){alpha=a;beta=b;gamma=g;seasonLength=s;seasonals=new double[s];java.util.Arrays.fill(seasonals,0.0);}        
        void update(double value){
            if(count<seasonLength){ seasonals[count]=value; level=value; trend=0; count++; return; }
            int idx = count % seasonLength;
            double lastLevel = level; double seasonal = seasonals[idx];
            level = alpha*(value - seasonal) + (1-alpha)*(level + trend);
            trend = beta*(level - lastLevel) + (1-beta)*trend;
            seasonals[idx] = gamma*(value - level) + (1-gamma)*seasonal;
            count++;
        }
        double forecast(int m){ int idx = (count + m) % seasonLength; return level + m*trend + seasonals[idx]; }
    }
}
