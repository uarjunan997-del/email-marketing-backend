package com.emailMarketing.roi;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.*; import java.io.Serializable;

@Entity @Table(name="EXCHANGE_RATES") @Getter @Setter
public class ExchangeRate {
    @EmbeddedId private ExchangeRateId id;
    @Column(name="RATE", nullable=false)
    private Double rate;
    @Column(name="SOURCE", length=40) private String source;
    @Column(name="CREATED_AT") private Instant createdAt = Instant.now();

    @Embeddable @Getter @Setter
    public static class ExchangeRateId implements Serializable {
        private LocalDate rateDate; private String baseCurrency; private String quoteCurrency;
        @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof ExchangeRateId that)) return false; return java.util.Objects.equals(rateDate,that.rateDate)&& java.util.Objects.equals(baseCurrency,that.baseCurrency)&& java.util.Objects.equals(quoteCurrency,that.quoteCurrency);} @Override public int hashCode(){ return java.util.Objects.hash(rateDate,baseCurrency,quoteCurrency);} }
}
