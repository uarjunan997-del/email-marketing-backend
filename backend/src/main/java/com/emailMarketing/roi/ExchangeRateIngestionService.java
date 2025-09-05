package com.emailMarketing.roi;

import com.emailMarketing.roi.repo.ExchangeRateRepository; import org.springframework.stereotype.Service; import org.springframework.web.client.RestTemplate; import org.springframework.beans.factory.annotation.Value; import org.springframework.transaction.annotation.Transactional; import java.time.*; import java.util.*;

@Service
public class ExchangeRateIngestionService {
    private final ExchangeRateRepository repo; private final RestTemplate rt = new RestTemplate();
    private final String base; private final String apiUrl;
    public ExchangeRateIngestionService(ExchangeRateRepository repo,
        @Value("${finance.base-currency:USD}") String base,
        @Value("${finance.fx.api-url:https://open.er-api.com/v6/latest}") String apiUrl){ this.repo=repo; this.base=base; this.apiUrl=apiUrl; }

    @Transactional
    public Map<String,Object> ingestLatest(){
        try {
            String url = apiUrl + "/" + base;
            @SuppressWarnings("unchecked") Map<String,Object> resp = rt.getForObject(url, Map.class);
            if(resp==null) return Map.of("status","NO_DATA");
            Object ratesObj = resp.get("rates"); if(!(ratesObj instanceof Map<?,?> ratesMap)) return Map.of("status","INVALID_RESPONSE");
            LocalDate today = LocalDate.now(); int saved=0;
            for(var e: ratesMap.entrySet()){
                String quote = e.getKey().toString(); if(quote.length()>10) continue; if(!(e.getValue() instanceof Number n)) continue;
                var id = new ExchangeRate.ExchangeRateId(); id.setRateDate(today); id.setBaseCurrency(base); id.setQuoteCurrency(quote);
                if(repo.findById(id).isPresent()) continue;
                ExchangeRate er = new ExchangeRate(); er.setId(id); er.setRate(n.doubleValue()); er.setSource("API"); repo.save(er); saved++;
            }
            return Map.of("status","OK","saved", saved);
        } catch(Exception ex){ return Map.of("status","ERROR","message", ex.getMessage()); }
    }
}
