package com.emailMarketing.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class EmailMetrics {
    private final MeterRegistry registry;
    private final ConcurrentMap<String, io.micrometer.core.instrument.Counter> counters = new ConcurrentHashMap<>();

    public EmailMetrics(MeterRegistry registry){
        this.registry = registry;
    }

    public void increment(String name, String resultType){
        String key = name+":"+resultType;
        counters.computeIfAbsent(key, k -> io.micrometer.core.instrument.Counter.builder("email.send.total")
                .tag("type", name)
                .tag("result", resultType)
                .description("Email send attempts")
                .register(registry))
            .increment();
    }

    public <T> T timed(String timerName, java.util.concurrent.Callable<T> c) throws Exception {
        var sample = io.micrometer.core.instrument.Timer.start(registry);
        try {
            T v = c.call();
            sample.stop(io.micrometer.core.instrument.Timer.builder(timerName).description("Timing for "+timerName).register(registry));
            return v;
        } catch(Exception ex){
            sample.stop(io.micrometer.core.instrument.Timer.builder(timerName).description("Timing for "+timerName).register(registry));
            throw ex;
        }
    }
}
